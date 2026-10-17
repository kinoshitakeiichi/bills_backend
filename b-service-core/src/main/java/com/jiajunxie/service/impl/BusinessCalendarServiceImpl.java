package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.convert.BusinessCalendarConvertServiceMapper;
import com.jiajunxie.entity.BusinessCalendar;
import com.jiajunxie.entity.BusinessCalendarDay;
import com.jiajunxie.entity.CreditCardProfile;
import com.jiajunxie.mapper.BusinessCalendarDayMapper;
import com.jiajunxie.mapper.BusinessCalendarMapper;
import com.jiajunxie.mapper.CreditCardProfileMapper;
import com.jiajunxie.service.BusinessCalendarService;
import com.jiajunxie.vo.BaseOptionVo;
import com.jiajunxie.vo.BusinessCalendarDayVo;
import com.jiajunxie.vo.BusinessCalendarVo;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BusinessCalendarServiceImpl implements BusinessCalendarService {
  private final BusinessCalendarMapper calendarMapper;
  private final BusinessCalendarDayMapper dayMapper;
  private final CreditCardProfileMapper profileMapper;
  private final BusinessCalendarConvertServiceMapper convertMapper;

  @Override
  public List<BusinessCalendarVo> list(String keyword, Boolean enabled) {
    String normalized = StringUtils.hasText(keyword) ? keyword.trim() : null;
    var query = Wrappers.<BusinessCalendar>lambdaQuery()
        .eq(enabled != null, BusinessCalendar::getEnabled, enabled)
        .and(StringUtils.hasText(normalized), w -> w.like(BusinessCalendar::getCalendarCode, normalized)
            .or().like(BusinessCalendar::getCalendarName, normalized)
            .or().like(BusinessCalendar::getCountryCode, normalized))
        .orderByAsc(BusinessCalendar::getCalendarCode);
    return convertMapper.toVos(calendarMapper.selectList(query));
  }

  @Override
  public List<BaseOptionVo> options() {
    return calendarMapper.selectList(Wrappers.<BusinessCalendar>lambdaQuery()
            .eq(BusinessCalendar::getEnabled, true)
            .orderByAsc(BusinessCalendar::getCalendarName))
        .stream()
        .map(item -> new BaseOptionVo(item.getCalendarCode(), item.getCalendarName(), true))
        .toList();
  }

  @Override
  @Transactional
  public int create(BusinessCalendarVo vo) {
    validate(vo, true);
    ensureCodeUnique(vo.getCalendarCode(), null);
    BusinessCalendar entity = convertMapper.toEntity(vo);
    entity.setCalendarId(UUID.randomUUID().toString());
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(entity.getCreatedAt());
    return calendarMapper.insert(entity);
  }

  @Override
  @Transactional
  public int update(String calendarId, BusinessCalendarVo vo) {
    BusinessCalendar entity = requireCalendar(calendarId);
    if (vo == null || !StringUtils.hasText(vo.getCalendarName())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "营业日日历名称不能为空");
    }
    // calendarCode is a stable business code and is intentionally immutable after creation.
    entity.setCalendarName(vo.getCalendarName().trim());
    entity.setCountryCode(normalizeNullableUpper(vo.getCountryCode()));
    if (vo.getEnabled() != null) entity.setEnabled(vo.getEnabled());
    entity.setUpdatedAt(Instant.now());
    return calendarMapper.updateById(entity);
  }

  @Override
  @Transactional
  public int setEnabled(String calendarId, boolean enabled) {
    BusinessCalendar entity = requireCalendar(calendarId);
    if (!enabled && profileMapper.selectCount(Wrappers.<CreditCardProfile>lambdaQuery()
        .eq(CreditCardProfile::getCalendarCode, entity.getCalendarCode())
        .eq(CreditCardProfile::getEnabled, true)) > 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "营业日日历仍被启用的信用卡配置引用");
    }
    entity.setEnabled(enabled);
    entity.setUpdatedAt(Instant.now());
    return calendarMapper.updateById(entity);
  }

  @Override
  public List<BusinessCalendarDayVo> listDays(String calendarId, LocalDate dateFrom, LocalDate dateTo) {
    requireCalendar(calendarId);
    var query = Wrappers.<BusinessCalendarDay>lambdaQuery()
        .eq(BusinessCalendarDay::getCalendarId, calendarId)
        .ge(dateFrom != null, BusinessCalendarDay::getCalendarDate, dateFrom)
        .le(dateTo != null, BusinessCalendarDay::getCalendarDate, dateTo)
        .orderByAsc(BusinessCalendarDay::getCalendarDate);
    return convertMapper.toDayVos(dayMapper.selectList(query));
  }

  @Override
  @Transactional
  public int upsertDay(String calendarId, LocalDate date, BusinessCalendarDayVo vo) {
    requireCalendar(calendarId);
    if (date == null || vo == null || vo.getBusinessDay() == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "日期和营业日状态不能为空");
    }
    BusinessCalendarDay existing = dayMapper.selectOne(Wrappers.<BusinessCalendarDay>lambdaQuery()
        .eq(BusinessCalendarDay::getCalendarId, calendarId)
        .eq(BusinessCalendarDay::getCalendarDate, date));
    if (existing == null) {
      BusinessCalendarDay entity = convertMapper.toDayEntity(vo);
      entity.setCalendarDayId(UUID.randomUUID().toString());
      entity.setCalendarId(calendarId);
      entity.setCalendarDate(date);
      entity.setHolidayName(normalizeNullable(vo.getHolidayName()));
      entity.setSource(normalizeNullableUpper(vo.getSource()));
      entity.setCreatedAt(Instant.now());
      entity.setUpdatedAt(entity.getCreatedAt());
      return dayMapper.insert(entity);
    }
    existing.setBusinessDay(vo.getBusinessDay());
    existing.setHolidayName(normalizeNullable(vo.getHolidayName()));
    existing.setSource(normalizeNullableUpper(vo.getSource()));
    existing.setUpdatedAt(Instant.now());
    return dayMapper.updateById(existing);
  }

  @Override
  @Transactional
  public int removeDay(String calendarId, LocalDate date) {
    requireCalendar(calendarId);
    return dayMapper.delete(Wrappers.<BusinessCalendarDay>lambdaQuery()
        .eq(BusinessCalendarDay::getCalendarId, calendarId)
        .eq(BusinessCalendarDay::getCalendarDate, date));
  }

  @Override
  public boolean isBusinessDay(String calendarCode, LocalDate date) {
    if (date == null) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "日期不能为空");
    if (StringUtils.hasText(calendarCode)) {
      BusinessCalendar calendar = calendarMapper.selectOne(Wrappers.<BusinessCalendar>lambdaQuery()
          .eq(BusinessCalendar::getCalendarCode, calendarCode.trim().toUpperCase(Locale.ROOT)));
      if (calendar == null) {
        throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "营业日日历不存在：" + calendarCode);
      }
      BusinessCalendarDay override = dayMapper.selectOne(Wrappers.<BusinessCalendarDay>lambdaQuery()
          .eq(BusinessCalendarDay::getCalendarId, calendar.getCalendarId())
          .eq(BusinessCalendarDay::getCalendarDate, date));
      if (override != null) return Boolean.TRUE.equals(override.getBusinessDay());
    }
    DayOfWeek day = date.getDayOfWeek();
    return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
  }

  private BusinessCalendar requireCalendar(String calendarId) {
    if (!StringUtils.hasText(calendarId)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "营业日日历 ID 不能为空");
    }
    BusinessCalendar calendar = calendarMapper.selectById(calendarId);
    if (calendar == null) throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "营业日日历不存在");
    return calendar;
  }

  private void validate(BusinessCalendarVo vo, boolean requireCode) {
    if (vo == null || !StringUtils.hasText(vo.getCalendarName()) || (requireCode && !StringUtils.hasText(vo.getCalendarCode()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "营业日日历编码和名称不能为空");
    }
    if (StringUtils.hasText(vo.getCalendarCode())) vo.setCalendarCode(vo.getCalendarCode().trim().toUpperCase(Locale.ROOT));
    vo.setCalendarName(vo.getCalendarName().trim());
    vo.setCountryCode(normalizeNullableUpper(vo.getCountryCode()));
    if (vo.getEnabled() == null) vo.setEnabled(true);
  }

  private void ensureCodeUnique(String code, String excludedId) {
    var query = Wrappers.<BusinessCalendar>lambdaQuery().eq(BusinessCalendar::getCalendarCode, code)
        .ne(StringUtils.hasText(excludedId), BusinessCalendar::getCalendarId, excludedId);
    if (calendarMapper.selectCount(query) > 0) {
      throw new BusinessException(CommonErrorCode.DUPLICATE_DATA, "营业日日历编码已存在：" + code);
    }
  }

  private String normalizeNullable(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private String normalizeNullableUpper(String value) {
    return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
  }
}
