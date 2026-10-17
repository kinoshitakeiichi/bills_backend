package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.entity.CreditCardProfile;
import com.jiajunxie.entity.InstallmentPlan;
import com.jiajunxie.entity.PaymentSchedule;
import com.jiajunxie.mapper.CreditCardProfileMapper;
import com.jiajunxie.mapper.InstallmentPlanMapper;
import com.jiajunxie.mapper.PaymentScheduleMapper;
import com.jiajunxie.service.CreditCardDateRuleService;
import com.jiajunxie.vo.CreditCardDatePreviewVo;
import com.jiajunxie.vo.TransactionCreateVo;
import com.jiajunxie.vo.TransactionEntryVo;
import com.jiajunxie.vo.TransactionPaymentVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreditCardPaymentPlanServiceImplTest {

  @Test
  void installmentWithoutFeeGeneratesZeroFeeSchedules() {
    Fixture fixture = fixture();
    when(fixture.dateRuleService.previewSaved("card", LocalDate.of(2026, 8, 9)))
        .thenReturn(preview(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 10)));
    when(fixture.dateRuleService.previewSaved("card", LocalDate.of(2026, 8, 16)))
        .thenReturn(preview(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 10)));
    when(fixture.dateRuleService.previewSaved("card", LocalDate.of(2026, 9, 16)))
        .thenReturn(preview(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 10)));

    fixture.service.createPlan(
        "tx",
        transaction(
            new BigDecimal("100000.0000"),
            new TransactionPaymentVo(
                "INSTALLMENT", null, 3, BigDecimal.ZERO, "LAST_INSTALLMENT", null, null)));

    ArgumentCaptor<PaymentSchedule> captor = ArgumentCaptor.forClass(PaymentSchedule.class);
    verify(fixture.scheduleMapper, times(3)).insert(captor.capture());
    List<PaymentSchedule> schedules = captor.getAllValues();

    assertEquals(new BigDecimal("33333"), schedules.get(0).getScheduledPrincipalAmount());
    assertEquals(new BigDecimal("33333"), schedules.get(1).getScheduledPrincipalAmount());
    assertEquals(new BigDecimal("33334"), schedules.get(2).getScheduledPrincipalAmount());
    assertEquals(BigDecimal.ZERO.setScale(0), schedules.get(0).getScheduledFeeAmount());
    assertEquals(LocalDate.of(2026, 8, 15), schedules.get(0).getScheduledBillingDate());
  }

  @Test
  void feeRemainderIsSplitSeparatelyFromPrincipal() {
    Fixture fixture = fixture();
    when(fixture.dateRuleService.previewSaved(any(), any()))
        .thenReturn(preview(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 10)));

    fixture.service.createPlan(
        "tx",
        transaction(
            new BigDecimal("100000.0000"),
            new TransactionPaymentVo(
                "INSTALLMENT", null, 3, new BigDecimal("1000.0000"), "FIRST_INSTALLMENT", null, null)));

    ArgumentCaptor<PaymentSchedule> captor = ArgumentCaptor.forClass(PaymentSchedule.class);
    verify(fixture.scheduleMapper, times(3)).insert(captor.capture());
    List<PaymentSchedule> schedules = captor.getAllValues();

    assertEquals(new BigDecimal("33334"), schedules.get(0).getScheduledPrincipalAmount());
    assertEquals(new BigDecimal("334"), schedules.get(0).getScheduledFeeAmount());
    assertEquals(new BigDecimal("333"), schedules.get(1).getScheduledFeeAmount());
    assertEquals(new BigDecimal("333"), schedules.get(2).getScheduledFeeAmount());
  }

  @Test
  void configuredScaleTwoSplitsAtCentPrecision() {
    Fixture fixture = fixture();
    fixture.profile().setAmountScale(2);

    when(fixture.dateRuleService.previewSaved(any(), any()))
        .thenReturn(preview(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 10)));

    fixture.service.createPlan(
        "tx",
        transaction(
            new BigDecimal("10.01"),
            new TransactionPaymentVo(
                "INSTALLMENT", null, 3, BigDecimal.ZERO, "LAST_INSTALLMENT", null, null)));

    ArgumentCaptor<PaymentSchedule> captor = ArgumentCaptor.forClass(PaymentSchedule.class);
    verify(fixture.scheduleMapper, times(3)).insert(captor.capture());

    assertEquals(new BigDecimal("3.33"), captor.getAllValues().get(0).getScheduledPrincipalAmount());
    assertEquals(new BigDecimal("3.33"), captor.getAllValues().get(1).getScheduledPrincipalAmount());
    assertEquals(new BigDecimal("3.35"), captor.getAllValues().get(2).getScheduledPrincipalAmount());
  }


  @Test
  void manualRemainderUsesUserSuppliedInstallmentAmounts() {
    Fixture fixture = fixture();
    when(fixture.dateRuleService.previewSaved(any(), any()))
        .thenReturn(preview(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 10)));

    fixture.service.createPlan(
        "tx",
        transaction(
            new BigDecimal("10000"),
            new TransactionPaymentVo(
                "INSTALLMENT",
                null,
                3,
                new BigDecimal("100"),
                "MANUAL",
                List.of(new BigDecimal("3000"), new BigDecimal("3500"), new BigDecimal("3500")),
                List.of(new BigDecimal("30"), new BigDecimal("30"), new BigDecimal("40")))));

    ArgumentCaptor<PaymentSchedule> captor = ArgumentCaptor.forClass(PaymentSchedule.class);
    verify(fixture.scheduleMapper, times(3)).insert(captor.capture());
    List<PaymentSchedule> schedules = captor.getAllValues();
    assertEquals(new BigDecimal("3000"), schedules.get(0).getScheduledPrincipalAmount());
    assertEquals(new BigDecimal("3500"), schedules.get(1).getScheduledPrincipalAmount());
    assertEquals(new BigDecimal("3500"), schedules.get(2).getScheduledPrincipalAmount());
    assertEquals(new BigDecimal("30"), schedules.get(0).getScheduledFeeAmount());
    assertEquals(new BigDecimal("40"), schedules.get(2).getScheduledFeeAmount());
  }

  @Test
  void manualRemainderRejectsPrincipalSumMismatch() {
    Fixture fixture = fixture();

    assertThrows(
        BusinessException.class,
        () -> fixture.service.createPlan(
            "tx",
            transaction(
                new BigDecimal("10000"),
                new TransactionPaymentVo(
                    "INSTALLMENT",
                    null,
                    3,
                    BigDecimal.ZERO,
                    "MANUAL",
                    List.of(new BigDecimal("3000"), new BigDecimal("3000"), new BigDecimal("3000")),
                    List.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)))));
  }

  @Test
  void manualRemainderRejectsWrongInstallmentCount() {
    Fixture fixture = fixture();

    assertThrows(
        BusinessException.class,
        () -> fixture.service.createPlan(
            "tx",
            transaction(
                new BigDecimal("10000"),
                new TransactionPaymentVo(
                    "INSTALLMENT",
                    null,
                    3,
                    BigDecimal.ZERO,
                    "MANUAL",
                    List.of(new BigDecimal("5000"), new BigDecimal("5000")),
                    List.of(BigDecimal.ZERO, BigDecimal.ZERO)))));
  }

  @Test
  void scaleZeroRejectsFractionalPurchaseAmount() {
    Fixture fixture = fixture();

    assertThrows(
        BusinessException.class,
        () ->
            fixture.service.createPlan(
                "tx",
                transaction(
                    new BigDecimal("1000.50"),
                    new TransactionPaymentVo(
                        "NORMAL", null, 1, BigDecimal.ZERO, "LAST_INSTALLMENT", null, null))));
  }

  @Test
  void pendingRefundReducesPrincipalFromLastInstallmentFirst() {
    Fixture fixture = fixture();
    InstallmentPlan plan = new InstallmentPlan();
    plan.setInstallmentPlanId("plan");
    plan.setSourceTransactionId("tx");
    plan.setPrincipalAmount(new BigDecimal("100"));
    plan.setUpdatedAt(Instant.now());
    when(fixture.planMapper.selectOne(any())).thenReturn(plan);

    PaymentSchedule first = pendingSchedule("s1", 1, "40");
    PaymentSchedule second = pendingSchedule("s2", 2, "60");
    when(fixture.scheduleMapper.selectList(any())).thenReturn(List.of(first, second));

    fixture.service.applyPendingPrincipalRefund("tx", new BigDecimal("70"));

    assertEquals(new BigDecimal("30"), first.getScheduledPrincipalAmount());
    assertEquals(new BigDecimal("30"), plan.getPrincipalAmount());
    verify(fixture.scheduleMapper).deleteById("s2");
    verify(fixture.scheduleMapper).updateById(first);
    verify(fixture.planMapper).updateById(plan);
  }

  @Test
  void pendingRefundRejectsWhenAnyScheduleIsAlreadyBilled() {
    Fixture fixture = fixture();
    InstallmentPlan plan = new InstallmentPlan();
    plan.setInstallmentPlanId("plan");
    plan.setSourceTransactionId("tx");
    plan.setPrincipalAmount(new BigDecimal("100"));
    when(fixture.planMapper.selectOne(any())).thenReturn(plan);

    PaymentSchedule billed = pendingSchedule("s1", 1, "100");
    billed.setStatusCode("BILLED");
    when(fixture.scheduleMapper.selectList(any())).thenReturn(List.of(billed));

    assertThrows(
        BusinessException.class,
        () -> fixture.service.applyPendingPrincipalRefund("tx", new BigDecimal("10")));

    verify(fixture.planMapper, never()).updateById(any(InstallmentPlan.class));
  }

  @Test
  void pendingRefundRejectsAmountAboveRemainingPrincipal() {
    Fixture fixture = fixture();
    InstallmentPlan plan = new InstallmentPlan();
    plan.setInstallmentPlanId("plan");
    plan.setSourceTransactionId("tx");
    plan.setPrincipalAmount(new BigDecimal("100"));
    when(fixture.planMapper.selectOne(any())).thenReturn(plan);
    when(fixture.scheduleMapper.selectList(any()))
        .thenReturn(List.of(pendingSchedule("s1", 1, "100")));

    assertThrows(
        BusinessException.class,
        () -> fixture.service.applyPendingPrincipalRefund("tx", new BigDecimal("101")));
  }

  @Test
  void normalGeneratesOnePlanAndOneSchedule() {
    Fixture fixture = fixture();
    when(fixture.dateRuleService.previewSaved("card", LocalDate.of(2026, 8, 9)))
        .thenReturn(preview(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 10)));

    fixture.service.createPlan(
        "tx",
        transaction(
            new BigDecimal("20000.0000"),
            new TransactionPaymentVo(
                "NORMAL", null, 1, BigDecimal.ZERO, "LAST_INSTALLMENT", null, null)));

    ArgumentCaptor<InstallmentPlan> planCaptor = ArgumentCaptor.forClass(InstallmentPlan.class);
    ArgumentCaptor<PaymentSchedule> scheduleCaptor = ArgumentCaptor.forClass(PaymentSchedule.class);
    verify(fixture.planMapper).insert(planCaptor.capture());
    verify(fixture.scheduleMapper).insert(scheduleCaptor.capture());

    assertNotNull(planCaptor.getValue().getCreatedAt());
    assertNotNull(planCaptor.getValue().getUpdatedAt());
    assertNotNull(scheduleCaptor.getValue().getCreatedAt());
    assertNotNull(scheduleCaptor.getValue().getUpdatedAt());
  }

  private Fixture fixture() {
    InstallmentPlanMapper planMapper = mock(InstallmentPlanMapper.class);
    PaymentScheduleMapper scheduleMapper = mock(PaymentScheduleMapper.class);
    CreditCardProfileMapper profileMapper = mock(CreditCardProfileMapper.class);
    CreditCardDateRuleService dateRuleService = mock(CreditCardDateRuleService.class);

    CreditCardProfile profile = new CreditCardProfile();
    profile.setAccountId("card");
    profile.setEnabled(true);
    profile.setAmountScale(0);
    profile.setTimezone("Asia/Tokyo");
    profile.setPaymentMonthOffset(1);
    profile.setBillingEndMonthOffset(0);
    profile.setBillingEndDay(15);
    profile.setInvalidDayRuleCode("LAST_DAY");
    when(profileMapper.selectById("card")).thenReturn(profile);

    return new Fixture(
        new CreditCardPaymentPlanServiceImpl(
            planMapper,
            scheduleMapper,
            profileMapper,
            dateRuleService,
            mock(com.jiajunxie.mapper.CreditRefundScheduleChangeMapper.class)),
        planMapper,
        scheduleMapper,
        dateRuleService,
        profile);
  }

  private PaymentSchedule pendingSchedule(String id, int no, String principal) {
    PaymentSchedule schedule = new PaymentSchedule();
    schedule.setScheduleId(id);
    schedule.setInstallmentPlanId("plan");
    schedule.setSourceTransactionId("tx");
    schedule.setLiabilityAccountId("card");
    schedule.setInstallmentNo(no);
    schedule.setInstallmentCount(2);
    schedule.setScheduledPrincipalAmount(new BigDecimal(principal));
    schedule.setScheduledFeeAmount(BigDecimal.ZERO);
    schedule.setAdjustmentAmount(BigDecimal.ZERO);
    schedule.setStatusCode("PENDING");
    schedule.setUpdatedAt(Instant.now());
    return schedule;
  }

  private TransactionCreateVo transaction(BigDecimal amount, TransactionPaymentVo payment) {
    return new TransactionCreateVo(
        null,
        "CREDIT_PURCHASE",
        "category",
        null,
        null,
        Instant.parse("2026-08-09T03:00:00Z"),
        List.of(new TransactionEntryVo("card", amount, "JPY", "SINGLE")),
        List.of(),
        payment,
        List.of());
  }

  private CreditCardDatePreviewVo preview(LocalDate periodEnd, LocalDate paymentDate) {
    CreditCardDatePreviewVo preview = new CreditCardDatePreviewVo();
    preview.setPeriodStart(periodEnd.withDayOfMonth(1));
    preview.setPeriodEnd(periodEnd);
    preview.setScheduledPaymentDate(paymentDate);
    preview.setCalculatedPaymentDate(paymentDate);
    preview.setPaymentOptions(List.of());
    return preview;
  }

  private record Fixture(
      CreditCardPaymentPlanServiceImpl service,
      InstallmentPlanMapper planMapper,
      PaymentScheduleMapper scheduleMapper,
      CreditCardDateRuleService dateRuleService,
      CreditCardProfile profile) {}
}
