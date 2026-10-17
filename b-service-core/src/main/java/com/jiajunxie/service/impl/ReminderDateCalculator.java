package com.jiajunxie.service.impl;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.CreditCardProfile;
import com.jiajunxie.entity.LoanProfile;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CreditCardProfileMapper;
import com.jiajunxie.mapper.LoanProfileMapper;
import com.jiajunxie.service.BusinessCalendarService;
import com.jiajunxie.service.CreditCardDateRuleService;
import com.jiajunxie.vo.CreditCardDatePreviewVo;
import com.jiajunxie.vo.ReminderDatePreviewVo;
import com.jiajunxie.vo.ReminderVo;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ReminderDateCalculator {

  private final AccountMapper accountMapper;
  private final CreditCardProfileMapper creditCardProfileMapper;
  private final LoanProfileMapper loanProfileMapper;
  private final BusinessCalendarService businessCalendarService;
  private final CreditCardDateRuleService creditCardDateRuleService;

  public ReminderDatePreviewVo preview(ReminderVo reminder, LocalDate referenceDate) {
    if (reminder == null || referenceDate == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "提醒和参考日期不能为空");
    }
    String source = normalize(reminder.dateRuleSourceCode(), "CUSTOM");
    return "ACCOUNT".equals(source)
        ? previewFromAccount(reminder, referenceDate)
        : previewCustom(reminder, referenceDate);
  }

  private ReminderDatePreviewVo previewFromAccount(ReminderVo reminder, LocalDate referenceDate) {
    if (!StringUtils.hasText(reminder.accountId())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "继承账户设置时必须关联账户");
    }
    Account account = accountMapper.selectById(reminder.accountId());
    if (account == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "关联账户不存在");
    }

    return switch (normalize(reminder.reminderTypeCode(), "CUSTOM")) {
      case "CREDIT_CARD_PAYMENT" -> creditCardPayment(reminder, referenceDate);
      case "BILLING_DATE" -> creditCardBilling(reminder, referenceDate);
      case "LOAN_PAYMENT" -> loanPayment(reminder, referenceDate);
      default ->
          throw new BusinessException(
              CommonErrorCode.PARAMETER_ERROR, "自定义提醒不能继承账户日期规则，请改用自定义日期规则");
    };
  }

  private ReminderDatePreviewVo creditCardPayment(ReminderVo reminder, LocalDate referenceDate) {
    CreditCardProfile profile = creditCardProfileMapper.selectById(reminder.accountId());
    if (profile == null || !Boolean.TRUE.equals(profile.getEnabled())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "关联账户没有启用的信用卡日期设置");
    }
    CreditCardDatePreviewVo preview =
        creditCardDateRuleService.previewSaved(reminder.accountId(), referenceDate);
    return new ReminderDatePreviewVo(
        preview.getScheduledPaymentDate(),
        preview.getCalculatedPaymentDate(),
        "ACCOUNT",
        normalize(profile.getInvalidDayRuleCode(), "LAST_DAY"),
        profile.getCalendarCode(),
        normalize(profile.getBusinessDayAdjustmentCode(), "NONE"),
        StringUtils.hasText(profile.getTimezone()) ? profile.getTimezone() : reminder.timezone(),
        "继承信用卡还款日设置");
  }

  private ReminderDatePreviewVo creditCardBilling(ReminderVo reminder, LocalDate referenceDate) {
    CreditCardProfile profile = creditCardProfileMapper.selectById(reminder.accountId());
    if (profile == null || !Boolean.TRUE.equals(profile.getEnabled())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "关联账户没有启用的信用卡日期设置");
    }

    String invalidRule = normalize(profile.getInvalidDayRuleCode(), "LAST_DAY");
    String adjustment = normalize(profile.getBusinessDayAdjustmentCode(), "NONE");
    LocalDate scheduled =
        nextMonthlyDate(
            referenceDate,
            profile.getBillingEndDay(),
            invalidRule,
            profile.getCalendarCode(),
            adjustment).scheduled();
    LocalDate calculated = adjustBusinessDay(scheduled, profile.getCalendarCode(), adjustment);

    return new ReminderDatePreviewVo(
        scheduled,
        calculated,
        "ACCOUNT",
        invalidRule,
        profile.getCalendarCode(),
        adjustment,
        StringUtils.hasText(profile.getTimezone()) ? profile.getTimezone() : reminder.timezone(),
        "继承信用卡账期结束日设置");
  }

  private ReminderDatePreviewVo loanPayment(ReminderVo reminder, LocalDate referenceDate) {
    LoanProfile profile = loanProfileMapper.selectById(reminder.accountId());
    if (profile == null) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "关联账户没有贷款设置");
    }
    if (profile.getPaymentDay() == null) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款账户没有配置还款日");
    }
    String adjustment = normalize(profile.getBusinessDayAdjustmentCode(), "NONE");
    DatePair pair =
        nextMonthlyDate(
            referenceDate,
            profile.getPaymentDay(),
            "LAST_DAY",
            profile.getCalendarCode(),
            adjustment);
    return new ReminderDatePreviewVo(
        pair.scheduled(),
        pair.calculated(),
        "ACCOUNT",
        "LAST_DAY",
        profile.getCalendarCode(),
        adjustment,
        StringUtils.hasText(reminder.timezone()) ? reminder.timezone() : "Asia/Tokyo",
        "继承贷款还款日设置");
  }

  private ReminderDatePreviewVo previewCustom(ReminderVo reminder, LocalDate referenceDate) {
    String invalidRule = normalize(reminder.invalidDayRuleCode(), "LAST_DAY");
    String adjustment = normalize(reminder.businessDayAdjustmentCode(), "NONE");
    String mode = normalize(reminder.scheduleModeCode(), "MONTHLY");

    LocalDate scheduled;
    LocalDate calculated;
    if ("ONCE".equals(mode)) {
      if (reminder.remindDate() == null) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "单次提醒必须设置日期");
      }
      scheduled = reminder.remindDate();
      calculated = adjustBusinessDay(scheduled, reminder.calendarCode(), adjustment);
    } else if ("MONTHLY".equals(mode)) {
      if (reminder.dayOfMonth() == null) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "每月提醒必须设置日期");
      }
      DatePair pair =
          nextMonthlyDate(
              referenceDate,
              reminder.dayOfMonth(),
              invalidRule,
              reminder.calendarCode(),
              adjustment);
      scheduled = pair.scheduled();
      calculated = pair.calculated();
    } else {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "不支持的提醒周期");
    }

    return new ReminderDatePreviewVo(
        scheduled,
        calculated,
        "CUSTOM",
        invalidRule,
        reminder.calendarCode(),
        adjustment,
        StringUtils.hasText(reminder.timezone()) ? reminder.timezone() : "Asia/Tokyo",
        "使用提醒自定义日期规则");
  }

  private DatePair nextMonthlyDate(
      LocalDate referenceDate,
      int day,
      String invalidRule,
      String calendarCode,
      String adjustment) {
    if (day < 1 || day > 31) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "每月日期必须为 1-31");
    }
    YearMonth month = YearMonth.from(referenceDate);
    LocalDate scheduled = resolveDay(month, day, invalidRule);
    LocalDate calculated = adjustBusinessDay(scheduled, calendarCode, adjustment);
    if (calculated.isBefore(referenceDate)) {
      month = month.plusMonths(1);
      scheduled = resolveDay(month, day, invalidRule);
      calculated = adjustBusinessDay(scheduled, calendarCode, adjustment);
    }
    return new DatePair(scheduled, calculated);
  }

  private LocalDate resolveDay(YearMonth month, int day, String invalidRule) {
    if (day <= month.lengthOfMonth()) return month.atDay(day);
    return switch (invalidRule) {
      case "NEXT_MONTH_FIRST_DAY" -> month.plusMonths(1).atDay(1);
      case "LAST_DAY" -> month.atEndOfMonth();
      default ->
          throw new BusinessException(
              CommonErrorCode.PARAMETER_ERROR, "不支持的无效日期规则：" + invalidRule);
    };
  }

  private LocalDate adjustBusinessDay(LocalDate date, String calendarCode, String adjustmentRule) {
    String rule = normalize(adjustmentRule, "NONE");
    return switch (rule) {
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
      default ->
          throw new BusinessException(
              CommonErrorCode.PARAMETER_ERROR, "不支持的营业日调整规则：" + rule);
    };
  }

  private LocalDate move(LocalDate date, String calendarCode, int direction) {
    LocalDate candidate = date;
    int guard = 0;
    while (!businessCalendarService.isBusinessDay(calendarCode, candidate)) {
      candidate = candidate.plusDays(direction);
      if (++guard > 31) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "31 天内找不到可用营业日");
      }
    }
    return candidate;
  }

  private String normalize(String value, String fallback) {
    return StringUtils.hasText(value)
        ? value.trim().toUpperCase(Locale.ROOT)
        : fallback;
  }

  private record DatePair(LocalDate scheduled, LocalDate calculated) {}
}
