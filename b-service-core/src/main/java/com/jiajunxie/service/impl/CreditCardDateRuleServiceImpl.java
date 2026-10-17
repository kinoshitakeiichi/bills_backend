package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.convert.CreditCardProfileConvertServiceMapper;
import com.jiajunxie.entity.CreditCardProfile;
import com.jiajunxie.entity.CreditPaymentRule;
import com.jiajunxie.mapper.CreditCardProfileMapper;
import com.jiajunxie.mapper.CreditPaymentRuleMapper;
import com.jiajunxie.service.BusinessCalendarService;
import com.jiajunxie.service.CreditCardDateRuleService;
import com.jiajunxie.vo.CreditCardDatePreviewVo;
import com.jiajunxie.vo.CreditCardProfileVo;
import com.jiajunxie.vo.CreditCardPaymentOptionVo;
import com.jiajunxie.vo.CreditPaymentRuleVo;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CreditCardDateRuleServiceImpl implements CreditCardDateRuleService {
  private final CreditCardProfileMapper profileMapper;
  private final CreditPaymentRuleMapper ruleMapper;
  private final CreditCardProfileConvertServiceMapper convertMapper;
  private final BusinessCalendarService calendarService;

  @Override
  public CreditCardDatePreviewVo previewSaved(String accountId, LocalDate referenceDate) {
    CreditCardProfile profile = profileMapper.selectById(accountId);
    if (profile == null) throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "信用卡账单配置不存在");
    CreditCardProfileVo vo = convertMapper.toVo(profile);
    List<CreditPaymentRuleVo> rules = convertMapper.toRuleVos(ruleMapper.selectList(
        Wrappers.<CreditPaymentRule>lambdaQuery()
            .eq(CreditPaymentRule::getAccountId, accountId)
            .eq(CreditPaymentRule::getEnabled, true)));
    vo.setRules(rules);
    return calculate(vo, referenceDate);
  }

  @Override
  public CreditCardDatePreviewVo previewDraft(CreditCardProfileVo profile, LocalDate referenceDate) {
    return calculate(profile, referenceDate);
  }

  private CreditCardDatePreviewVo calculate(CreditCardProfileVo profile, LocalDate referenceDate) {
    if (profile == null || referenceDate == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "Profile 和参考日期不能为空");
    }
    requireDay(profile.getBillingStartDay(), "账期开始日");
    requireDay(profile.getBillingEndDay(), "账期结束日");
    requireDay(profile.getPaymentDay(), "支付日");
    int endOffset = profile.getBillingEndMonthOffset() == null ? 0 : profile.getBillingEndMonthOffset();
    int confirmationOffset = profile.getConfirmationMonthOffset() == null ? 0 : profile.getConfirmationMonthOffset();
    int paymentOffset = profile.getPaymentMonthOffset() == null ? 1 : profile.getPaymentMonthOffset();
    String invalidDayRule = normalize(profile.getInvalidDayRuleCode(), "LAST_DAY");
    String adjustmentRule = normalize(profile.getBusinessDayAdjustmentCode(), "NONE");

    LocalDate periodStart = null;
    LocalDate periodEnd = null;
    YearMonth referenceMonth = YearMonth.from(referenceDate);
    for (int delta = -3; delta <= 1; delta++) {
      YearMonth startMonth = referenceMonth.plusMonths(delta);
      LocalDate start = resolveDay(startMonth, profile.getBillingStartDay(), invalidDayRule);
      LocalDate end = resolveDay(startMonth.plusMonths(endOffset), profile.getBillingEndDay(), invalidDayRule);
      if (!end.isBefore(start) && !referenceDate.isBefore(start) && !referenceDate.isAfter(end)) {
        periodStart = start;
        periodEnd = end;
        break;
      }
    }
    if (periodStart == null) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "当前账期规则无法覆盖参考日期，请检查账期开始/结束和月份偏移");
    }

    YearMonth anchor = YearMonth.from(periodStart);
    LocalDate confirmation = profile.getConfirmationDay() == null ? null
        : resolveDay(anchor.plusMonths(confirmationOffset), profile.getConfirmationDay(), invalidDayRule);
    // NORMAL is always calculated from the card profile and is never silently overridden by a
    // SUMMER/WINTER/SPECIAL rule. Special rules are alternative choices shown by Preview.
    LocalDate normalScheduledPayment =
        resolveDay(anchor.plusMonths(paymentOffset), profile.getPaymentDay(), invalidDayRule);
    LocalDate normalCalculatedPayment =
        adjustBusinessDay(normalScheduledPayment, profile.getCalendarCode(), adjustmentRule);

    List<CreditCardPaymentOptionVo> paymentOptions = new ArrayList<>();
    for (CreditPaymentRuleVo rule : matchingRules(profile.getRules(), referenceDate)) {
      LocalDate specialScheduledPayment =
          resolveSpecialPaymentDate(rule, referenceDate, invalidDayRule);
      LocalDate specialCalculatedPayment =
          adjustBusinessDay(specialScheduledPayment, profile.getCalendarCode(), adjustmentRule);
      paymentOptions.add(
          paymentOption(
              rule.getRuleTypeCode(),
              rule.getRuleId(),
              rule.getPriority(),
              specialScheduledPayment,
              specialCalculatedPayment));
    }

    // NORMAL is always available as the fallback choice. It intentionally has no priority because
    // priority belongs to configured special rules and only controls their recommendation order.
    paymentOptions.add(
        paymentOption(
            "NORMAL", null, null, normalScheduledPayment, normalCalculatedPayment));

    CreditCardDatePreviewVo result = new CreditCardDatePreviewVo();
    result.setReferenceDate(referenceDate);
    result.setPeriodStart(periodStart);
    result.setPeriodEnd(periodEnd);
    result.setConfirmationDate(confirmation);
    result.setScheduledPaymentDate(normalScheduledPayment);
    result.setCalculatedPaymentDate(normalCalculatedPayment);
    result.setPaymentOptions(paymentOptions);
    return result;
  }

  /** Resolves invalid month days without ever converting the calculation through UTC. */
  private LocalDate resolveDay(YearMonth month, int day, String invalidRule) {
    if (day <= month.lengthOfMonth()) return month.atDay(day);
    return switch (invalidRule) {
      case "NEXT_MONTH_FIRST_DAY" -> month.plusMonths(1).atDay(1);
      case "LAST_DAY" -> month.atEndOfMonth();
      default -> throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "不支持的无效日期规则：" + invalidRule);
    };
  }

  private LocalDate adjustBusinessDay(LocalDate date, String calendarCode, String adjustmentRule) {
    return switch (adjustmentRule) {
      case "NONE" -> date;
      case "FOLLOWING", "NEXT_BUSINESS_DAY" -> move(date, calendarCode, 1);
      case "PRECEDING", "PREVIOUS_BUSINESS_DAY" -> move(date, calendarCode, -1);
      case "MODIFIED_FOLLOWING" -> {
        LocalDate moved = move(date, calendarCode, 1);
        yield moved.getMonth() == date.getMonth() ? moved : move(date, calendarCode, -1);
      }
      case "MODIFIED_PRECEDING" -> {
        LocalDate moved = move(date, calendarCode, -1);
        yield moved.getMonth() == date.getMonth() ? moved : move(date, calendarCode, 1);
      }
      default -> throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "不支持的营业日调整规则：" + adjustmentRule);
    };
  }

  private LocalDate move(LocalDate date, String calendarCode, int direction) {
    LocalDate candidate = date;
    int guard = 0;
    while (!calendarService.isBusinessDay(calendarCode, candidate)) {
      candidate = candidate.plusDays(direction);
      if (++guard > 31) throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "31 天内找不到可用营业日");
    }
    return candidate;
  }


  /**
   * Resolves the next actual target date for a matched special-payment rule.
   *
   * <p>The rule range may cross a year boundary (for example SUMMER 12-28..06-27).
   * The target month/day is therefore interpreted as the next occurrence that is not before
   * the purchase date. This correctly maps both 2026-12-30 and 2027-01-10 to 2027-07-27,
   * while WINTER 06-28..12-27 maps purchases to the following January.
   */
  private LocalDate resolveSpecialPaymentDate(
      CreditPaymentRuleVo rule, LocalDate referenceDate, String invalidDayRule) {
    if (rule.getTargetMonth() == null || rule.getTargetMonth() < 1 || rule.getTargetMonth() > 12) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "特殊规则目标支付月必须为 1-12");
    }
    requireDay(rule.getTargetDay(), "特殊规则目标支付日");

    int targetYear = referenceDate.getYear();
    LocalDate candidate = resolveDay(
        YearMonth.of(targetYear, rule.getTargetMonth()), rule.getTargetDay(), invalidDayRule);
    if (candidate.isBefore(referenceDate)) {
      targetYear++;
      candidate = resolveDay(
          YearMonth.of(targetYear, rule.getTargetMonth()), rule.getTargetDay(), invalidDayRule);
    }
    return candidate;
  }

  /**
   * Returns every enabled special-payment rule available for the purchase date.
   *
   * <p>Priority only controls display/recommendation order. It must never auto-select a payment
   * method on behalf of the user.
   */
  private List<CreditPaymentRuleVo> matchingRules(
      List<CreditPaymentRuleVo> rules, LocalDate referenceDate) {
    if (rules == null || rules.isEmpty()) return List.of();
    MonthDay reference = MonthDay.from(referenceDate);
    return rules.stream()
        .filter(rule -> Boolean.TRUE.equals(rule.getEnabled()))
        .filter(
            rule ->
                inRange(
                    reference,
                    MonthDay.parse("--" + rule.getPurchaseStartMonthDay()),
                    MonthDay.parse("--" + rule.getPurchaseEndMonthDay())))
        .sorted(
            Comparator.comparing(
                    CreditPaymentRuleVo::getPriority,
                    Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                    CreditPaymentRuleVo::getRuleTypeCode,
                    Comparator.nullsLast(String::compareTo))
                .thenComparing(
                    CreditPaymentRuleVo::getRuleId,
                    Comparator.nullsLast(String::compareTo)))
        .toList();
  }

  private CreditCardPaymentOptionVo paymentOption(
      String paymentTypeCode,
      String ruleId,
      Integer priority,
      LocalDate scheduledPaymentDate,
      LocalDate calculatedPaymentDate) {
    CreditCardPaymentOptionVo option = new CreditCardPaymentOptionVo();
    option.setPaymentTypeCode(paymentTypeCode);
    option.setRuleId(ruleId);
    option.setPriority(priority);
    option.setScheduledPaymentDate(scheduledPaymentDate);
    option.setCalculatedPaymentDate(calculatedPaymentDate);
    return option;
  }

  private boolean inRange(MonthDay value, MonthDay start, MonthDay end) {
    if (start.compareTo(end) <= 0) return value.compareTo(start) >= 0 && value.compareTo(end) <= 0;
    return value.compareTo(start) >= 0 || value.compareTo(end) <= 0;
  }

  private void requireDay(Integer day, String name) {
    if (day == null || day < 1 || day > 31) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, name + "必须为 1-31");
  }

  private String normalize(String value, String defaultValue) {
    return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : defaultValue;
  }
}
