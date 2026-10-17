package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.Reminder;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.ReminderMapper;
import com.jiajunxie.service.BarkNotificationService;
import com.jiajunxie.service.ExchangeRateAlertService;
import com.jiajunxie.service.ReminderService;
import com.jiajunxie.vo.BarkNotificationVo;
import com.jiajunxie.vo.ExchangeRateAlertSettingsVo;
import com.jiajunxie.vo.ReminderDatePreviewVo;
import com.jiajunxie.vo.ReminderVo;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderServiceImpl implements ReminderService {

  private final ReminderMapper reminderMapper;
  private final AccountMapper accountMapper;
  private final BarkNotificationService barkNotificationService;
  private final ExchangeRateAlertService exchangeRateAlertService;
  private final ReminderDateCalculator dateCalculator;

  @Override
  public List<ReminderVo> list(Boolean enabled) {
    return reminderMapper
        .selectList(
            Wrappers.<Reminder>lambdaQuery()
                .eq(enabled != null, Reminder::getEnabled, enabled)
                .orderByDesc(Reminder::getEnabled)
                .orderByAsc(Reminder::getReminderName))
        .stream()
        .map(this::toVo)
        .toList();
  }

  @Override
  @Transactional
  public ReminderVo create(ReminderVo vo) {
    validate(vo);
    dateCalculator.preview(vo, LocalDate.now(ZoneId.of(effectiveTimezone(vo))));
    Reminder entity = new Reminder();
    entity.setReminderId(UUID.randomUUID().toString());
    apply(entity, vo);
    Instant now = Instant.now();
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    reminderMapper.insert(entity);
    return toVo(entity);
  }

  @Override
  @Transactional
  public ReminderVo update(String reminderId, ReminderVo vo) {
    Reminder entity = requireReminder(reminderId);
    validate(vo);
    dateCalculator.preview(vo, LocalDate.now(ZoneId.of(effectiveTimezone(vo))));
    apply(entity, vo);
    entity.setUpdatedAt(Instant.now());
    reminderMapper.updateById(entity);
    return toVo(entity);
  }

  @Override
  @Transactional
  public int setEnabled(String reminderId, boolean enabled) {
    Reminder entity = requireReminder(reminderId);
    entity.setEnabled(enabled);
    entity.setUpdatedAt(Instant.now());
    return reminderMapper.updateById(entity);
  }

  @Override
  @Transactional
  public int delete(String reminderId) {
    requireReminder(reminderId);
    return reminderMapper.deleteById(reminderId);
  }

  @Override
  @Transactional(readOnly = true)
  public ReminderDatePreviewVo preview(ReminderVo reminder, LocalDate referenceDate) {
    validate(reminder);
    return dateCalculator.preview(
        reminder,
        referenceDate == null ? LocalDate.now(ZoneId.of(effectiveTimezone(reminder))) : referenceDate);
  }

@Override
@Transactional
public void evaluateDueReminders() {
  Instant now = Instant.now();
  List<Reminder> reminders =
      reminderMapper.selectList(
          Wrappers.<Reminder>lambdaQuery().eq(Reminder::getEnabled, true));

  for (Reminder reminder : reminders) {
    try {
      ReminderVo vo = toVo(reminder);
      ZoneId zone = ZoneId.of(effectiveTimezone(vo));
      LocalDate referenceDate = now.atZone(zone).toLocalDate();
      ReminderDatePreviewVo preview = dateCalculator.preview(vo, referenceDate);
      ZoneId previewZone =
          StringUtils.hasText(preview.timezone())
              ? ZoneId.of(preview.timezone())
              : zone;
      Instant dueInstant =
          ZonedDateTime.of(
                  preview.calculatedDate(),
                  reminder.getRemindTime(),
                  previewZone)
              .toInstant();

      if (now.isBefore(dueInstant)) continue;
      if (now.isAfter(dueInstant.plus(10, ChronoUnit.MINUTES))) continue;
      if (alreadyTriggeredForOccurrence(reminder, dueInstant)) continue;

      if (send(reminder, preview)) {
        reminder.setLastTriggeredAt(now);
        if ("ONCE".equals(reminder.getScheduleModeCode())) {
          reminder.setEnabled(false);
        }
        reminder.setUpdatedAt(now);
        reminderMapper.updateById(reminder);
      }
    } catch (RuntimeException ex) {
      log.error("自定义提醒执行失败 reminderId={}", reminder.getReminderId(), ex);
    }
  }
}

  private boolean send(Reminder reminder, ReminderDatePreviewVo preview) {
    ExchangeRateAlertSettingsVo settings = exchangeRateAlertService.getSettings();
    if (!settings.barkDeviceKeyConfigured()) return false;

    String accountName = null;
    if (StringUtils.hasText(reminder.getAccountId())) {
      Account account = accountMapper.selectById(reminder.getAccountId());
      if (account != null) accountName = account.getAccountName();
    }

    String title =
        StringUtils.hasText(reminder.getNotificationTitle())
            ? reminder.getNotificationTitle()
            : reminder.getReminderName();
    String body =
        StringUtils.hasText(reminder.getNotificationBody())
            ? reminder.getNotificationBody()
            : defaultBody(reminder, accountName, preview);

    return barkNotificationService.send(
        settings.barkBaseUrl(),
        BarkNotificationVo.builder()
            .title(title)
            .body(body)
            .url(reminder.getNotificationUrl())
            .sound(settings.barkSound())
            .call(settings.barkCall())
            .group(
                StringUtils.hasText(settings.barkGroupName())
                    ? settings.barkGroupName()
                    : "记账助手提醒")
            .level(settings.barkLevel())
            .isArchive(settings.barkArchive())
            .volume(settings.barkVolume())
            .build());
  }

  private String defaultBody(
      Reminder reminder, String accountName, ReminderDatePreviewVo preview) {
    String type =
        switch (reminder.getReminderTypeCode()) {
          case "CREDIT_CARD_PAYMENT" -> "信用卡还款日提醒";
          case "BILLING_DATE" -> "账单日提醒";
          case "LOAN_PAYMENT" -> "贷款还款日提醒";
          default -> "自定义提醒";
        };
    String accountPart = accountName == null ? "" : " · " + accountName;
    return type
        + accountPart
        + "\n计划日期："
        + preview.scheduledDate()
        + "\n最终提醒日期："
        + preview.calculatedDate();
  }

  private boolean alreadyTriggeredForOccurrence(Reminder reminder, Instant dueInstant) {
    return reminder.getLastTriggeredAt() != null
        && !reminder.getLastTriggeredAt().isBefore(dueInstant);
  }

  private void validate(ReminderVo vo) {
    if (vo == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "提醒内容不能为空");
    }
    if (!StringUtils.hasText(vo.reminderName())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "提醒名称不能为空");
    }
    String type = normalizeCode(vo.reminderTypeCode());
    if (!List.of("CUSTOM", "CREDIT_CARD_PAYMENT", "BILLING_DATE", "LOAN_PAYMENT").contains(type)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "不支持的提醒类型");
    }
    String mode = normalizeCode(vo.scheduleModeCode());
    if (!List.of("ONCE", "MONTHLY").contains(mode)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "不支持的提醒周期");
    }
    if (vo.remindTime() == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "提醒时间不能为空");
    }

    String dateRuleSource = normalizeCode(vo.dateRuleSourceCode());
    if (!List.of("CUSTOM", "ACCOUNT").contains(dateRuleSource)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "不支持的日期规则来源");
    }
    if ("ACCOUNT".equals(dateRuleSource)) {
      if (!StringUtils.hasText(vo.accountId())) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "继承账户设置时必须关联账户");
      }
      if ("CUSTOM".equals(type)) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "自定义提醒不能继承账户日期规则");
      }
    } else {
      if ("ONCE".equals(mode) && vo.remindDate() == null) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "单次提醒必须设置日期");
      }
      if ("MONTHLY".equals(mode)
          && (vo.dayOfMonth() == null || vo.dayOfMonth() < 1 || vo.dayOfMonth() > 31)) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "每月提醒日必须为 1-31");
      }
      String invalidDay = normalizeCode(vo.invalidDayRuleCode());
      if (!List.of("LAST_DAY", "NEXT_MONTH_FIRST_DAY").contains(invalidDay)) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "不支持的无效日期规则");
      }
      String adjustment = normalizeCode(vo.businessDayAdjustmentCode());
      if (!List.of(
              "NONE",
              "FOLLOWING",
              "NEXT_BUSINESS_DAY",
              "PRECEDING",
              "PREVIOUS_BUSINESS_DAY",
              "MODIFIED_FOLLOWING",
              "MODIFIED_PRECEDING")
          .contains(adjustment)) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "不支持的营业日调整规则");
      }
    }
    if (StringUtils.hasText(vo.accountId()) && accountMapper.selectById(vo.accountId()) == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "关联账户不存在");
    }
    if (StringUtils.hasText(vo.timezone())) {
      try {
        ZoneId.of(vo.timezone());
      } catch (RuntimeException ex) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "无效时区");
      }
    }
    if (StringUtils.hasText(vo.notificationUrl())) {
      String url = vo.notificationUrl().trim();
      if (!(url.startsWith("http://")
          || url.startsWith("https://")
          || url.matches("^[A-Za-z][A-Za-z0-9+.-]*://.+$"))) {
        throw new BusinessException(
            CommonErrorCode.PARAMETER_ERROR, "通知跳转 URL 必须是有效的 URL 或 App Scheme");
      }
    }
  }

  private void apply(Reminder entity, ReminderVo vo) {
    String mode = normalizeCode(vo.scheduleModeCode());
    entity.setReminderName(vo.reminderName().trim());
    entity.setReminderTypeCode(normalizeCode(vo.reminderTypeCode()));
    entity.setAccountId(trimToNull(vo.accountId()));
    entity.setScheduleModeCode(mode);
    entity.setRemindDate("ONCE".equals(mode) ? vo.remindDate() : null);
    entity.setDayOfMonth("MONTHLY".equals(mode) ? vo.dayOfMonth() : null);
    entity.setRemindTime(vo.remindTime());
    entity.setTimezone(
        StringUtils.hasText(vo.timezone()) ? vo.timezone().trim() : "Asia/Tokyo");
    entity.setDateRuleSourceCode(
        StringUtils.hasText(vo.dateRuleSourceCode())
            ? normalizeCode(vo.dateRuleSourceCode())
            : "CUSTOM");
    entity.setInvalidDayRuleCode(
        StringUtils.hasText(vo.invalidDayRuleCode())
            ? normalizeCode(vo.invalidDayRuleCode())
            : "LAST_DAY");
    entity.setCalendarCode(trimToNull(vo.calendarCode()));
    entity.setBusinessDayAdjustmentCode(
        StringUtils.hasText(vo.businessDayAdjustmentCode())
            ? normalizeCode(vo.businessDayAdjustmentCode())
            : "NONE");
    entity.setNotificationUrl(trimToNull(vo.notificationUrl()));
    entity.setNotificationTitle(trimToNull(vo.notificationTitle()));
    entity.setNotificationBody(trimToNull(vo.notificationBody()));
    entity.setEnabled(vo.enabled() == null || Boolean.TRUE.equals(vo.enabled()));
  }

  private Reminder requireReminder(String reminderId) {
    Reminder entity = reminderMapper.selectById(reminderId);
    if (entity == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "提醒不存在");
    }
    return entity;
  }

  private ReminderVo toVo(Reminder entity) {
    return new ReminderVo(
        entity.getReminderId(),
        entity.getReminderName(),
        entity.getReminderTypeCode(),
        entity.getAccountId(),
        entity.getScheduleModeCode(),
        entity.getRemindDate(),
        entity.getDayOfMonth(),
        entity.getRemindTime(),
        entity.getTimezone(),
        entity.getDateRuleSourceCode(),
        entity.getInvalidDayRuleCode(),
        entity.getCalendarCode(),
        entity.getBusinessDayAdjustmentCode(),
        entity.getNotificationUrl(),
        entity.getNotificationTitle(),
        entity.getNotificationBody(),
        entity.getEnabled(),
        entity.getLastTriggeredAt(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private String effectiveTimezone(ReminderVo vo) {
    return StringUtils.hasText(vo.timezone()) ? vo.timezone().trim() : "Asia/Tokyo";
  }

  private String normalizeCode(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private String trimToNull(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
