package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jiajunxie.convert.CreditCardProfileConvertServiceMapper;
import com.jiajunxie.mapper.CreditCardProfileMapper;
import com.jiajunxie.mapper.CreditPaymentRuleMapper;
import com.jiajunxie.service.BusinessCalendarService;
import com.jiajunxie.vo.CreditCardDatePreviewVo;
import com.jiajunxie.vo.CreditCardProfileVo;
import com.jiajunxie.vo.CreditCardPaymentOptionVo;
import com.jiajunxie.vo.CreditPaymentRuleVo;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreditCardDateRuleServiceImplTest {

  @Test
  void previewCrossMonthPeriodAndFollowingBusinessDay() {
    BusinessCalendarService calendarService = mock(BusinessCalendarService.class);
    when(calendarService.isBusinessDay(anyString(), any(LocalDate.class)))
        .thenAnswer(invocation -> {
          LocalDate date = invocation.getArgument(1);
          return date.getDayOfWeek().getValue() <= 5;
        });

    var service = new CreditCardDateRuleServiceImpl(
        mock(CreditCardProfileMapper.class),
        mock(CreditPaymentRuleMapper.class),
        mock(CreditCardProfileConvertServiceMapper.class),
        calendarService);

    CreditCardProfileVo profile = profile();
    CreditCardDatePreviewVo preview = service.previewDraft(profile, LocalDate.of(2026, 8, 20));

    assertEquals(LocalDate.of(2026, 8, 16), preview.getPeriodStart());
    assertEquals(LocalDate.of(2026, 9, 15), preview.getPeriodEnd());
    assertEquals(LocalDate.of(2026, 9, 20), preview.getConfirmationDate());
    assertEquals(LocalDate.of(2026, 10, 10), preview.getScheduledPaymentDate());
    assertEquals(LocalDate.of(2026, 10, 12), preview.getCalculatedPaymentDate());
    assertEquals(1, preview.getPaymentOptions().size());
    assertEquals("NORMAL", preview.getPaymentOptions().get(0).getPaymentTypeCode());
  }

  @Test
  void lastDayRuleUsesLeapYearMonthEnd() {
    BusinessCalendarService calendarService = mock(BusinessCalendarService.class);
    when(calendarService.isBusinessDay(anyString(), any(LocalDate.class))).thenReturn(true);
    var service = new CreditCardDateRuleServiceImpl(
        mock(CreditCardProfileMapper.class), mock(CreditPaymentRuleMapper.class),
        mock(CreditCardProfileConvertServiceMapper.class), calendarService);

    CreditCardProfileVo profile = profile();
    profile.setBillingStartDay(1);
    profile.setBillingEndDay(31);
    profile.setBillingEndMonthOffset(0);
    profile.setPaymentDay(31);
    profile.setPaymentMonthOffset(6);

    CreditCardDatePreviewVo preview = service.previewDraft(profile, LocalDate.of(2028, 2, 10));
    assertEquals(LocalDate.of(2028, 2, 29), preview.getPeriodEnd());
  }



  @Test
  void summerRuleIsReturnedAsChoiceWithoutOverridingNormalPayment() {
    var service = serviceWithWeekdays();
    CreditCardProfileVo profile = profile();
    profile.setRules(List.of(rule("SUMMER", "12-28", "06-27", 7, 27, 100)));

    CreditCardDatePreviewVo december =
        service.previewDraft(profile, LocalDate.of(2026, 12, 28));

    assertEquals(LocalDate.of(2027, 2, 10), december.getScheduledPaymentDate());

    CreditCardPaymentOptionVo summer = option(december, "SUMMER");
    assertEquals(100, summer.getPriority());
    assertEquals(LocalDate.of(2027, 7, 27), summer.getScheduledPaymentDate());

    CreditCardPaymentOptionVo normal = option(december, "NORMAL");
    assertEquals(LocalDate.of(2027, 2, 10), normal.getScheduledPaymentDate());

    CreditCardDatePreviewVo january =
        service.previewDraft(profile, LocalDate.of(2027, 1, 1));
    assertEquals(LocalDate.of(2027, 7, 27), option(january, "SUMMER").getScheduledPaymentDate());

    CreditCardDatePreviewVo june =
        service.previewDraft(profile, LocalDate.of(2027, 6, 27));
    assertEquals(LocalDate.of(2027, 7, 27), option(june, "SUMMER").getScheduledPaymentDate());
  }

  @Test
  void summerRuleIsNotReturnedOutsideCrossYearWindow() {
    var service = serviceWithWeekdays();
    CreditCardProfileVo profile = profile();
    profile.setRules(List.of(rule("SUMMER", "12-28", "06-27", 7, 27, 100)));

    assertEquals(
        List.of("NORMAL"),
        service.previewDraft(profile, LocalDate.of(2026, 12, 27)).getPaymentOptions().stream()
            .map(CreditCardPaymentOptionVo::getPaymentTypeCode)
            .toList());
    assertEquals(
        List.of("NORMAL"),
        service.previewDraft(profile, LocalDate.of(2027, 6, 28)).getPaymentOptions().stream()
            .map(CreditCardPaymentOptionVo::getPaymentTypeCode)
            .toList());
  }

  @Test
  void winterRuleUsesFollowingJanuaryTargetAsAlternativeChoice() {
    var service = serviceWithWeekdays();
    CreditCardProfileVo profile = profile();
    profile.setRules(List.of(rule("WINTER", "06-28", "12-27", 1, 27, 100)));

    CreditCardDatePreviewVo june =
        service.previewDraft(profile, LocalDate.of(2027, 6, 28));
    assertEquals(LocalDate.of(2028, 1, 27), option(june, "WINTER").getScheduledPaymentDate());

    CreditCardDatePreviewVo december =
        service.previewDraft(profile, LocalDate.of(2027, 12, 27));
    assertEquals(LocalDate.of(2028, 1, 27), option(december, "WINTER").getScheduledPaymentDate());
  }

  @Test
  void winterRuleIsNotReturnedOutsideWindow() {
    var service = serviceWithWeekdays();
    CreditCardProfileVo profile = profile();
    profile.setRules(List.of(rule("WINTER", "06-28", "12-27", 1, 27, 100)));

    assertEquals(
        List.of("NORMAL"),
        service.previewDraft(profile, LocalDate.of(2027, 6, 27)).getPaymentOptions().stream()
            .map(CreditCardPaymentOptionVo::getPaymentTypeCode)
            .toList());
    assertEquals(
        List.of("NORMAL"),
        service.previewDraft(profile, LocalDate.of(2027, 12, 28)).getPaymentOptions().stream()
            .map(CreditCardPaymentOptionVo::getPaymentTypeCode)
            .toList());
  }

  @Test
  void specialRuleTargetStillUsesBusinessDayAdjustment() {
    BusinessCalendarService calendarService = mock(BusinessCalendarService.class);
    when(calendarService.isBusinessDay(anyString(), any(LocalDate.class)))
        .thenAnswer(
            invocation -> {
              LocalDate date = invocation.getArgument(1);
              return !date.equals(LocalDate.of(2028, 1, 27));
            });
    var service =
        new CreditCardDateRuleServiceImpl(
            mock(CreditCardProfileMapper.class),
            mock(CreditPaymentRuleMapper.class),
            mock(CreditCardProfileConvertServiceMapper.class),
            calendarService);
    CreditCardProfileVo profile = profile();
    profile.setRules(List.of(rule("WINTER", "06-28", "12-27", 1, 27, 100)));

    CreditCardDatePreviewVo preview =
        service.previewDraft(profile, LocalDate.of(2027, 7, 1));
    CreditCardPaymentOptionVo winter = option(preview, "WINTER");
    assertEquals(LocalDate.of(2028, 1, 27), winter.getScheduledPaymentDate());
    assertEquals(LocalDate.of(2028, 1, 28), winter.getCalculatedPaymentDate());
  }

  @Test
  void previewReturnsAllMatchingSpecialRulesByPriorityThenNormalFallback() {
    var service = serviceWithWeekdays();
    CreditCardProfileVo profile = profile();

    CreditPaymentRuleVo summer = rule("SUMMER", "12-28", "06-27", 7, 27, 100);
    summer.setRuleId("summer");
    CreditPaymentRuleVo specialHigh = rule("SPECIAL", "12-01", "01-31", 3, 15, 300);
    specialHigh.setRuleId("special-high");
    CreditPaymentRuleVo specialMiddle =
        rule("WINTER_SPECIAL", "12-15", "02-01", 4, 10, 200);
    specialMiddle.setRuleId("special-middle");
    profile.setRules(List.of(summer, specialHigh, specialMiddle));

    CreditCardDatePreviewVo preview =
        service.previewDraft(profile, LocalDate.of(2026, 12, 30));

    assertEquals(
        List.of("SPECIAL", "WINTER_SPECIAL", "SUMMER", "NORMAL"),
        preview.getPaymentOptions().stream()
            .map(CreditCardPaymentOptionVo::getPaymentTypeCode)
            .toList());
    assertEquals(300, preview.getPaymentOptions().get(0).getPriority());
    assertEquals(200, preview.getPaymentOptions().get(1).getPriority());
    assertEquals(100, preview.getPaymentOptions().get(2).getPriority());
    assertEquals(null, preview.getPaymentOptions().get(3).getPriority());
  }

  private CreditCardPaymentOptionVo option(
      CreditCardDatePreviewVo preview, String paymentTypeCode) {
    return preview.getPaymentOptions().stream()
        .filter(option -> paymentTypeCode.equals(option.getPaymentTypeCode()))
        .findFirst()
        .orElseThrow();
  }

  private CreditCardProfileVo profile() {
    CreditCardProfileVo profile = new CreditCardProfileVo();
    profile.setBillingStartDay(16);
    profile.setBillingEndDay(15);
    profile.setBillingEndMonthOffset(1);
    profile.setConfirmationDay(20);
    profile.setConfirmationMonthOffset(1);
    profile.setPaymentDay(10);
    profile.setPaymentMonthOffset(2);
    profile.setInvalidDayRuleCode("LAST_DAY");
    profile.setBusinessDayAdjustmentCode("FOLLOWING");
    profile.setCalendarCode("JP_CARD");
    profile.setTimezone("Asia/Tokyo");
    profile.setRemainderRuleCode("LAST_INSTALLMENT");
    profile.setEnabled(true);
    return profile;
  }


  private CreditCardDateRuleServiceImpl serviceWithWeekdays() {
    BusinessCalendarService calendarService = mock(BusinessCalendarService.class);
    when(calendarService.isBusinessDay(anyString(), any(LocalDate.class)))
        .thenAnswer(invocation -> {
          LocalDate date = invocation.getArgument(1);
          return date.getDayOfWeek().getValue() <= 5;
        });
    return new CreditCardDateRuleServiceImpl(
        mock(CreditCardProfileMapper.class),
        mock(CreditPaymentRuleMapper.class),
        mock(CreditCardProfileConvertServiceMapper.class),
        calendarService);
  }

  private CreditPaymentRuleVo rule(
      String type, String start, String end, int targetMonth, int targetDay, int priority) {
    CreditPaymentRuleVo rule = new CreditPaymentRuleVo();
    rule.setRuleTypeCode(type);
    rule.setPurchaseStartMonthDay(start);
    rule.setPurchaseEndMonthDay(end);
    rule.setTargetMonth(targetMonth);
    rule.setTargetDay(targetDay);
    rule.setPriority(priority);
    rule.setEnabled(true);
    return rule;
  }

}
