package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.convert.CreditCardProfileConvertServiceMapper;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.BusinessCalendar;
import com.jiajunxie.entity.CreditCardProfile;
import com.jiajunxie.entity.CreditPaymentRule;
import com.jiajunxie.entity.DictionaryItem;
import com.jiajunxie.entity.DictionaryType;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.BusinessCalendarMapper;
import com.jiajunxie.mapper.CreditCardProfileMapper;
import com.jiajunxie.mapper.CreditPaymentRuleMapper;
import com.jiajunxie.mapper.DictionaryItemMapper;
import com.jiajunxie.mapper.DictionaryTypeMapper;
import com.jiajunxie.service.CreditCardProfileService;
import com.jiajunxie.vo.CreditCardProfileVo;
import com.jiajunxie.vo.CreditPaymentRuleVo;
import java.time.Instant;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CreditCardProfileServiceImpl implements CreditCardProfileService {
  private static final Set<String> PAYMENT_RULE_TYPES = Set.of("SUMMER", "WINTER", "SPECIAL", "DEFERRED");

  private final AccountMapper accountMapper;
  private final CreditCardProfileMapper profileMapper;
  private final CreditPaymentRuleMapper ruleMapper;
  private final BusinessCalendarMapper calendarMapper;
  private final DictionaryTypeMapper dictionaryTypeMapper;
  private final DictionaryItemMapper dictionaryItemMapper;
  private final CreditCardProfileConvertServiceMapper convertMapper;

  @Override
  public CreditCardProfileVo getProfile(String accountId) {
    CreditCardProfile profile = profileMapper.selectById(accountId);
    if (profile == null) return null;
    CreditCardProfileVo vo = convertMapper.toVo(profile);
    vo.setRules(listRules(accountId));
    return vo;
  }

  @Override
  @Transactional
  public int upsertProfile(String accountId, CreditCardProfileVo vo) {
    Account account = requireCreditCardAccount(accountId);
    validateProfile(vo);
    CreditCardProfile existing = profileMapper.selectById(accountId);
    Instant now = Instant.now();
    if (existing == null) {
      CreditCardProfile entity = convertMapper.toEntity(vo);
      entity.setAccountId(account.getAccountId());
      entity.setCreatedAt(now);
      entity.setUpdatedAt(now);
      return profileMapper.insert(entity);
    }
    Instant createdAt = existing.getCreatedAt();
    convertMapper.updateEntity(vo, existing);
    existing.setAccountId(accountId);
    existing.setCreatedAt(createdAt);
    existing.setUpdatedAt(now);
    return profileMapper.updateById(existing);
  }

  @Override
  public List<CreditPaymentRuleVo> listRules(String accountId) {
    return convertMapper.toRuleVos(ruleMapper.selectList(Wrappers.<CreditPaymentRule>lambdaQuery()
        .eq(CreditPaymentRule::getAccountId, accountId)
        .orderByDesc(CreditPaymentRule::getPriority)
        .orderByAsc(CreditPaymentRule::getRuleTypeCode)));
  }

  @Override
  @Transactional
  public int createRule(String accountId, CreditPaymentRuleVo vo) {
    requireProfile(accountId);
    validateRule(vo);
    ensureRuleConflictFree(accountId, vo, null);
    CreditPaymentRule entity = convertMapper.toRuleEntity(vo);
    entity.setRuleId(UUID.randomUUID().toString());
    entity.setAccountId(accountId);
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(entity.getCreatedAt());
    return ruleMapper.insert(entity);
  }

  @Override
  @Transactional
  public int updateRule(String accountId, String ruleId, CreditPaymentRuleVo vo) {
    requireProfile(accountId);
    CreditPaymentRule entity = requireRule(accountId, ruleId);
    validateRule(vo);
    ensureRuleConflictFree(accountId, vo, ruleId);
    Instant createdAt = entity.getCreatedAt();
    convertMapper.updateRuleEntity(vo, entity);
    entity.setRuleId(ruleId);
    entity.setAccountId(accountId);
    entity.setCreatedAt(createdAt);
    entity.setUpdatedAt(Instant.now());
    return ruleMapper.updateById(entity);
  }

  @Override
  @Transactional
  public int setRuleEnabled(String accountId, String ruleId, boolean enabled) {
    CreditPaymentRule entity = requireRule(accountId, ruleId);
    entity.setEnabled(enabled);
    entity.setUpdatedAt(Instant.now());
    return ruleMapper.updateById(entity);
  }

  private Account requireCreditCardAccount(String accountId) {
    Account account = accountMapper.selectById(accountId);
    if (account == null) throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "信用卡账户不存在");
    if (!Boolean.TRUE.equals(account.getEnabled())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "信用卡账户已停用");
    }
    if (!"LIABILITY".equalsIgnoreCase(account.getAccountClassCode())
        || !"CREDIT_CARD".equalsIgnoreCase(account.getAccountTypeCode())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "只有启用的信用卡负债账户可以维护账单配置");
    }
    return account;
  }

  private CreditCardProfile requireProfile(String accountId) {
    requireCreditCardAccount(accountId);
    CreditCardProfile profile = profileMapper.selectById(accountId);
    if (profile == null) throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "信用卡账单配置不存在");
    return profile;
  }

  private CreditPaymentRule requireRule(String accountId, String ruleId) {
    CreditPaymentRule rule = ruleMapper.selectById(ruleId);
    if (rule == null || !accountId.equals(rule.getAccountId())) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "信用卡特殊支付规则不存在");
    }
    return rule;
  }

  private void validateProfile(CreditCardProfileVo vo) {
    if (vo == null) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "信用卡配置不能为空");
    requireDay(vo.getBillingStartDay(), "账期开始日");
    requireDay(vo.getBillingEndDay(), "账期结束日");
    if (vo.getConfirmationDay() != null) requireDay(vo.getConfirmationDay(), "确认日");
    requireDay(vo.getPaymentDay(), "支付日");
    if (vo.getBillingEndMonthOffset() == null) vo.setBillingEndMonthOffset(0);
    if (vo.getConfirmationMonthOffset() == null) vo.setConfirmationMonthOffset(0);
    if (vo.getPaymentMonthOffset() == null) vo.setPaymentMonthOffset(1);
    if (vo.getBillingEndMonthOffset() < 0 || vo.getBillingEndMonthOffset() > 3
        || vo.getConfirmationMonthOffset() < 0 || vo.getConfirmationMonthOffset() > 6
        || vo.getPaymentMonthOffset() < 0 || vo.getPaymentMonthOffset() > 12) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "月份偏移超出允许范围");
    }
    if (!StringUtils.hasText(vo.getTimezone())) vo.setTimezone("Asia/Tokyo");
    try { ZoneId.of(vo.getTimezone().trim()); }
    catch (Exception e) { throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "无效的 IANA 时区：" + vo.getTimezone()); }
    vo.setTimezone(vo.getTimezone().trim());
    vo.setInvalidDayRuleCode(normalizeUpper(vo.getInvalidDayRuleCode(), "无效日期规则不能为空"));
    vo.setBusinessDayAdjustmentCode(normalizeUpper(vo.getBusinessDayAdjustmentCode(), "营业日调整规则不能为空"));
    vo.setRemainderRuleCode(normalizeUpper(vo.getRemainderRuleCode(), "余数规则不能为空"));
    requireEnabledDictionaryCode("INVALID_DAY_RULE", vo.getInvalidDayRuleCode());
    requireEnabledDictionaryCode("BUSINESS_DAY_ADJUSTMENT", vo.getBusinessDayAdjustmentCode());
    requireEnabledDictionaryCode("REMAINDER_RULE", vo.getRemainderRuleCode());
    if (StringUtils.hasText(vo.getCalendarCode())) {
      vo.setCalendarCode(vo.getCalendarCode().trim().toUpperCase(Locale.ROOT));
      BusinessCalendar calendar = calendarMapper.selectOne(Wrappers.<BusinessCalendar>lambdaQuery()
          .eq(BusinessCalendar::getCalendarCode, vo.getCalendarCode()));
      if (calendar == null) throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "营业日日历不存在：" + vo.getCalendarCode());
      if (!Boolean.TRUE.equals(calendar.getEnabled())) throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "营业日日历已停用：" + vo.getCalendarCode());
    } else {
      vo.setCalendarCode(null);
    }
    if (vo.getCreditLimit() != null && vo.getCreditLimit().signum() < 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "信用额度不能为负数");
    }
    if (vo.getAmountScale() == null) vo.setAmountScale(0);
    if (vo.getAmountScale() < 0 || vo.getAmountScale() > 4) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "金额小数位必须为 0-4");
    }
    if (vo.getEnabled() == null) vo.setEnabled(true);
  }

  private void validateRule(CreditPaymentRuleVo vo) {
    if (vo == null) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "特殊支付规则不能为空");
    vo.setRuleTypeCode(normalizeUpper(vo.getRuleTypeCode(), "规则类型不能为空"));
    if (!PAYMENT_RULE_TYPES.contains(vo.getRuleTypeCode())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "不支持的特殊支付规则：" + vo.getRuleTypeCode());
    }
    vo.setPurchaseStartMonthDay(normalizeMonthDay(vo.getPurchaseStartMonthDay(), "购买区间开始日期"));
    vo.setPurchaseEndMonthDay(normalizeMonthDay(vo.getPurchaseEndMonthDay(), "购买区间结束日期"));
    if (vo.getTargetMonth() == null || vo.getTargetMonth() < 1 || vo.getTargetMonth() > 12) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "目标支付月份必须为 1-12");
    }
    if (vo.getTargetDay() == null || vo.getTargetDay() < 1 || vo.getTargetDay() > 31) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "目标支付日必须为 1-31");
    }
    if (vo.getPriority() == null) vo.setPriority(0);
    if (vo.getPriority() < 0) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "规则优先级不能为负数");
    if (vo.getEnabled() == null) vo.setEnabled(true);
  }

  private void ensureRuleConflictFree(String accountId, CreditPaymentRuleVo vo, String excludedRuleId) {
    var query = Wrappers.<CreditPaymentRule>lambdaQuery()
        .eq(CreditPaymentRule::getAccountId, accountId)
        .eq(CreditPaymentRule::getRuleTypeCode, vo.getRuleTypeCode())
        .eq(CreditPaymentRule::getPurchaseStartMonthDay, vo.getPurchaseStartMonthDay())
        .eq(CreditPaymentRule::getPurchaseEndMonthDay, vo.getPurchaseEndMonthDay())
        .eq(CreditPaymentRule::getPriority, vo.getPriority())
        .ne(StringUtils.hasText(excludedRuleId), CreditPaymentRule::getRuleId, excludedRuleId);
    if (ruleMapper.selectCount(query) > 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "存在相同购买区间和优先级的特殊支付规则");
    }
  }

  private void requireEnabledDictionaryCode(String typeCode, String itemCode) {
    DictionaryType type = dictionaryTypeMapper.selectOne(Wrappers.<DictionaryType>lambdaQuery()
        .eq(DictionaryType::getTypeCode, typeCode).eq(DictionaryType::getEnabled, true));
    if (type == null) throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "字典类型不存在或已停用：" + typeCode);
    long count = dictionaryItemMapper.selectCount(Wrappers.<DictionaryItem>lambdaQuery()
        .eq(DictionaryItem::getDictionaryTypeId, type.getDictionaryTypeId())
        .eq(DictionaryItem::getItemCode, itemCode)
        .eq(DictionaryItem::getEnabled, true));
    if (count == 0) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "字典项不存在或已停用：" + typeCode + "/" + itemCode);
  }

  private void requireDay(Integer value, String label) {
    if (value == null || value < 1 || value > 31) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, label + "必须为 1-31");
    }
  }

  private String normalizeUpper(String value, String message) {
    if (!StringUtils.hasText(value)) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, message);
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeMonthDay(String value, String label) {
    if (!StringUtils.hasText(value)) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, label + "不能为空");
    String normalized = value.trim();
    try { MonthDay.parse("--" + normalized); }
    catch (DateTimeParseException e) { throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, label + "格式必须为 MM-DD 且日期有效"); }
    return normalized;
  }
}
