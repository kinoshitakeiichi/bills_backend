package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.CreditCardProfile;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CreditCardProfileMapper;
import com.jiajunxie.mapper.LoanProfileMapper;
import com.jiajunxie.service.BusinessCalendarService;
import com.jiajunxie.service.CreditCardDateRuleService;
import com.jiajunxie.vo.CreditCardDatePreviewVo;
import com.jiajunxie.vo.ReminderVo;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ReminderDateCalculatorTest {

  @Test
  void customMonthlyReminderUsesBusinessCalendarAdjustment() {
    BusinessCalendarService calendar = mock(BusinessCalendarService.class);
    when(calendar.isBusinessDay(any(), any(LocalDate.class)))
        .thenAnswer(
            invocation -> {
              LocalDate date = invocation.getArgument(1);
              DayOfWeek day = date.getDayOfWeek();
              return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
            });

    ReminderDateCalculator calculator =
        new ReminderDateCalculator(
            mock(AccountMapper.class),
            mock(CreditCardProfileMapper.class),
            mock(LoanProfileMapper.class),
            calendar,
            mock(CreditCardDateRuleService.class));

    ReminderVo reminder =
        reminder(
            "CUSTOM",
            null,
            "MONTHLY",
            null,
            31,
            "CUSTOM",
            "LAST_DAY",
            "JP",
            "FOLLOWING");

    var preview = calculator.preview(reminder, LocalDate.of(2026, 10, 1));

    assertEquals(LocalDate.of(2026, 10, 31), preview.scheduledDate());
    assertEquals(LocalDate.of(2026, 11, 2), preview.calculatedDate());
  }

  @Test
  void creditCardPaymentInheritanceReusesSavedCreditCardDateRule() {
    AccountMapper accountMapper = mock(AccountMapper.class);
    CreditCardProfileMapper profileMapper = mock(CreditCardProfileMapper.class);
    CreditCardDateRuleService dateRule = mock(CreditCardDateRuleService.class);

    Account account = new Account();
    account.setAccountId("card");
    when(accountMapper.selectById("card")).thenReturn(account);

    CreditCardProfile profile = new CreditCardProfile();
    profile.setAccountId("card");
    profile.setEnabled(true);
    profile.setInvalidDayRuleCode("LAST_DAY");
    profile.setBusinessDayAdjustmentCode("FOLLOWING");
    profile.setCalendarCode("JP");
    profile.setTimezone("Asia/Tokyo");
    when(profileMapper.selectById("card")).thenReturn(profile);

    CreditCardDatePreviewVo cardPreview = new CreditCardDatePreviewVo();
    cardPreview.setScheduledPaymentDate(LocalDate.of(2026, 9, 20));
    cardPreview.setCalculatedPaymentDate(LocalDate.of(2026, 9, 21));
    when(dateRule.previewSaved("card", LocalDate.of(2026, 8, 15))).thenReturn(cardPreview);

    ReminderDateCalculator calculator =
        new ReminderDateCalculator(
            accountMapper,
            profileMapper,
            mock(LoanProfileMapper.class),
            mock(BusinessCalendarService.class),
            dateRule);

    var preview =
        calculator.preview(
            reminder(
                "CREDIT_CARD_PAYMENT",
                "card",
                "MONTHLY",
                null,
                null,
                "ACCOUNT",
                "LAST_DAY",
                null,
                "NONE"),
            LocalDate.of(2026, 8, 15));

    assertEquals(LocalDate.of(2026, 9, 20), preview.scheduledDate());
    assertEquals(LocalDate.of(2026, 9, 21), preview.calculatedDate());
    assertEquals("JP", preview.calendarCode());
    assertEquals("FOLLOWING", preview.businessDayAdjustmentCode());
  }

  private ReminderVo reminder(
      String type,
      String accountId,
      String mode,
      LocalDate date,
      Integer day,
      String source,
      String invalidDay,
      String calendar,
      String adjustment) {
    return new ReminderVo(
        null,
        "提醒",
        type,
        accountId,
        mode,
        date,
        day,
        LocalTime.of(9, 0),
        "Asia/Tokyo",
        source,
        invalidDay,
        calendar,
        adjustment,
        null,
        null,
        null,
        true,
        null,
        null,
        null);
  }
}
