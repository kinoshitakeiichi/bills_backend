package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.domain.loan.LoanManualInstallmentInput;
import com.jiajunxie.domain.loan.LoanRemainderRule;
import com.jiajunxie.domain.loan.LoanRepaymentType;
import com.jiajunxie.domain.loan.LoanScheduleAdjustmentMode;
import com.jiajunxie.domain.loan.LoanScheduleGenerationCommand;
import com.jiajunxie.entity.InstallmentPlan;
import com.jiajunxie.entity.LoanProfile;
import com.jiajunxie.entity.LoanRateHistory;
import com.jiajunxie.entity.LoanRepaymentScheduleDetail;
import com.jiajunxie.entity.PaymentSchedule;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.InstallmentPlanMapper;
import com.jiajunxie.mapper.LoanProfileMapper;
import com.jiajunxie.mapper.LoanRateHistoryMapper;
import com.jiajunxie.mapper.LoanRepaymentScheduleDetailMapper;
import com.jiajunxie.mapper.ScheduleAdjustmentMapper;
import com.jiajunxie.mapper.LoanPaymentAllocationMapper;
import com.jiajunxie.mapper.PaymentScheduleMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.BusinessCalendarService;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LoanScheduleGenerationServiceImplTest {
  private LoanProfileMapper profileMapper;
  private LoanRateHistoryMapper rateHistoryMapper;
  private TransactionMapper transactionMapper;
  private TransactionEntryMapper entryMapper;
  private InstallmentPlanMapper planMapper;
  private PaymentScheduleMapper scheduleMapper;
  private LoanRepaymentScheduleDetailMapper detailMapper;
  private LoanPaymentAllocationMapper loanPaymentAllocationMapper;
  private ScheduleAdjustmentMapper scheduleAdjustmentMapper;
  private LoanLifecycleDeletionGuard loanLifecycleDeletionGuard;
  private BusinessCalendarService calendarService;
  private LoanScheduleGenerationServiceImpl service;

  @BeforeEach
  void setUp() {
    profileMapper = mock(LoanProfileMapper.class);
    rateHistoryMapper = mock(LoanRateHistoryMapper.class);
    transactionMapper = mock(TransactionMapper.class);
    entryMapper = mock(TransactionEntryMapper.class);
    planMapper = mock(InstallmentPlanMapper.class);
    scheduleMapper = mock(PaymentScheduleMapper.class);
    detailMapper = mock(LoanRepaymentScheduleDetailMapper.class);
    loanPaymentAllocationMapper = mock(LoanPaymentAllocationMapper.class);
    scheduleAdjustmentMapper = mock(ScheduleAdjustmentMapper.class);
    loanLifecycleDeletionGuard = mock(LoanLifecycleDeletionGuard.class);
    calendarService = mock(BusinessCalendarService.class);

    service =
        new LoanScheduleGenerationServiceImpl(
            profileMapper,
            rateHistoryMapper,
            transactionMapper,
            entryMapper,
            planMapper,
            scheduleMapper,
            detailMapper,
            loanPaymentAllocationMapper,
            scheduleAdjustmentMapper,
            loanLifecycleDeletionGuard,
            new LoanRepaymentCalculatorImpl(),
            calendarService);

    when(planMapper.selectCount(any())).thenReturn(0L);
    when(planMapper.insert(any(InstallmentPlan.class))).thenReturn(1);
    when(scheduleMapper.insert(any(PaymentSchedule.class))).thenReturn(1);
    when(detailMapper.insert(any(LoanRepaymentScheduleDetail.class))).thenReturn(1);
    when(rateHistoryMapper.selectList(any())).thenReturn(List.of());
    when(calendarService.isBusinessDay(any(), any(LocalDate.class)))
        .thenAnswer(
            invocation -> {
              LocalDate date = invocation.getArgument(1);
              DayOfWeek day = date.getDayOfWeek();
              return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
            });
  }

  @Test
  void accountDisbursementPersistsGenericSchedulesAndLoanSpecificDetails() {
    LoanProfile profile = profile("loan", "ACCOUNT_DISBURSEMENT", "FIXED", "0");
    when(profileMapper.selectById("loan")).thenReturn(profile);
    when(transactionMapper.selectById("funding")).thenReturn(transaction("funding", "LOAN_DISBURSEMENT", "loan"));
    when(entryMapper.selectList(any())).thenReturn(List.of(entry("funding", "loan", "100000")));

    var result =
        service.generate(
            command(
                "loan",
                "funding",
                LoanRepaymentType.ZERO_INTEREST,
                3,
                LocalDate.of(2026, 8, 31)));

    assertEquals(new BigDecimal("100000"), result.principalAmount());
    assertEquals(0, result.totalInterestAmount().signum());
    assertEquals(3, result.installmentCount());

    ArgumentCaptor<InstallmentPlan> planCaptor = ArgumentCaptor.forClass(InstallmentPlan.class);
    verify(planMapper).insert(planCaptor.capture());
    assertEquals("LOAN", planCaptor.getValue().getPaymentTypeCode());
    assertEquals("ROUND_TO_LAST", planCaptor.getValue().getRemainderRuleCode());

    ArgumentCaptor<PaymentSchedule> scheduleCaptor = ArgumentCaptor.forClass(PaymentSchedule.class);
    verify(scheduleMapper, times(3)).insert(scheduleCaptor.capture());
    List<PaymentSchedule> schedules = scheduleCaptor.getAllValues();
    assertEquals(new BigDecimal("33333"), schedules.get(0).getScheduledPrincipalAmount());
    assertEquals(new BigDecimal("33333"), schedules.get(1).getScheduledPrincipalAmount());
    assertEquals(new BigDecimal("33334"), schedules.get(2).getScheduledPrincipalAmount());
    assertEquals(LocalDate.of(2026, 10, 31), schedules.get(2).getScheduledPaymentDate());
    assertEquals(LocalDate.of(2026, 11, 2), schedules.get(2).getCalculatedPaymentDate());

    ArgumentCaptor<LoanRepaymentScheduleDetail> detailCaptor =
        ArgumentCaptor.forClass(LoanRepaymentScheduleDetail.class);
    verify(detailMapper, times(3)).insert(detailCaptor.capture());
    List<LoanRepaymentScheduleDetail> details = detailCaptor.getAllValues();
    assertEquals("ZERO_INTEREST", details.get(0).getRepaymentTypeCode());
    assertEquals(0, details.get(0).getScheduledInterestAmount().signum());
    assertEquals(new BigDecimal("66667"), details.get(0).getRemainingPrincipalAfter());
    assertEquals("LOAN_SCHEDULE_V1", details.get(0).getCalculationVersion());
  }

  @Test
  void purchaseFinancingUsesTheSelectedPurchaseAsItsOwnPrincipalSource() {
    LoanProfile profile = profile("loan", "PURCHASE_FINANCING", "FIXED", "0");
    when(profileMapper.selectById("loan")).thenReturn(profile);
    when(transactionMapper.selectById("purchase-b"))
        .thenReturn(transaction("purchase-b", "LOAN_PURCHASE", "loan"));
    when(entryMapper.selectList(any())).thenReturn(List.of(entry("purchase-b", "loan", "25000")));

    var result =
        service.generate(
            command(
                "loan",
                "purchase-b",
                LoanRepaymentType.ZERO_INTEREST,
                2,
                LocalDate.of(2026, 9, 30)));

    assertEquals("purchase-b", result.sourceTransactionId());
    assertEquals(new BigDecimal("25000"), result.principalAmount());

    ArgumentCaptor<InstallmentPlan> planCaptor = ArgumentCaptor.forClass(InstallmentPlan.class);
    verify(planMapper).insert(planCaptor.capture());
    assertEquals("purchase-b", planCaptor.getValue().getSourceTransactionId());
    assertEquals(new BigDecimal("25000"), planCaptor.getValue().getPrincipalAmount());
  }

  @Test
  void variableRateHistoryIsConvertedIntoPerInstallmentCalculationSnapshots() {
    LoanProfile profile = profile("loan", "DIRECT_FINANCING", "VARIABLE", "0.12");
    when(profileMapper.selectById("loan")).thenReturn(profile);
    when(transactionMapper.selectById("purchase"))
        .thenReturn(transaction("purchase", "LOAN_PURCHASE", "loan"));
    when(entryMapper.selectList(any())).thenReturn(List.of(entry("purchase", "loan", "120000")));

    LoanRateHistory first = rate("loan", LocalDate.of(2026, 1, 1), "0.12");
    LoanRateHistory changed = rate("loan", LocalDate.of(2027, 2, 1), "0.24");
    when(rateHistoryMapper.selectList(any())).thenReturn(List.of(first, changed));

    service.generate(
        command(
            "loan",
            "purchase",
            LoanRepaymentType.EQUAL_PAYMENT,
            12,
            LocalDate.of(2026, 9, 30)));

    ArgumentCaptor<LoanRepaymentScheduleDetail> captor =
        ArgumentCaptor.forClass(LoanRepaymentScheduleDetail.class);
    verify(detailMapper, times(12)).insert(captor.capture());
    List<LoanRepaymentScheduleDetail> details = captor.getAllValues();

    assertEquals(new BigDecimal("0.12"), details.get(0).getAnnualRateSnapshot());
    assertEquals(LocalDate.of(2026, 1, 1), details.get(0).getRateEffectiveFrom());
    assertEquals(new BigDecimal("0.24"), details.get(5).getAnnualRateSnapshot());
    assertEquals(LocalDate.of(2027, 2, 1), details.get(5).getRateEffectiveFrom());
    assertEquals(new BigDecimal("0.24"), details.get(11).getAnnualRateSnapshot());
  }

  @Test
  void manualInterestOnlyInstallmentPersistsAsLoanScheduleWithoutFakeGenericAdjustment() {
    LoanProfile profile = profile("loan", "DIRECT_FINANCING", "FIXED", "0");
    when(profileMapper.selectById("loan")).thenReturn(profile);
    when(transactionMapper.selectById("purchase"))
        .thenReturn(transaction("purchase", "LOAN_PURCHASE", "loan"));
    when(entryMapper.selectList(any())).thenReturn(List.of(entry("purchase", "loan", "100000")));

    LoanScheduleGenerationCommand command =
        new LoanScheduleGenerationCommand(
            "loan",
            "purchase",
            LoanRepaymentType.MANUAL,
            2,
            0,
            LocalDate.of(2026, 9, 30),
            LoanRemainderRule.ROUND_TO_LAST,
            LoanScheduleAdjustmentMode.ADJUST_LAST,
            List.of(
                new LoanManualInstallmentInput(
                    1, BigDecimal.ZERO, new BigDecimal("500"), BigDecimal.ZERO),
                new LoanManualInstallmentInput(
                    2, new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO)));

    service.generate(command);

    ArgumentCaptor<PaymentSchedule> scheduleCaptor = ArgumentCaptor.forClass(PaymentSchedule.class);
    verify(scheduleMapper, times(2)).insert(scheduleCaptor.capture());
    PaymentSchedule first = scheduleCaptor.getAllValues().get(0);
    assertEquals(0, first.getScheduledPrincipalAmount().signum());
    assertEquals(0, first.getScheduledFeeAmount().signum());
    assertEquals(0, first.getAdjustmentAmount().signum());
    assertEquals("LOAN", first.getPaymentTypeCode());

    ArgumentCaptor<LoanRepaymentScheduleDetail> detailCaptor =
        ArgumentCaptor.forClass(LoanRepaymentScheduleDetail.class);
    verify(detailMapper, times(2)).insert(detailCaptor.capture());
    assertEquals(
        new BigDecimal("500"),
        detailCaptor.getAllValues().get(0).getScheduledInterestAmount());
  }

  @Test
  void fundingModeRejectsUnrelatedLoanLifecycleFact() {
    LoanProfile profile = profile("loan", "ACCOUNT_DISBURSEMENT", "FIXED", "0");
    when(profileMapper.selectById("loan")).thenReturn(profile);
    when(transactionMapper.selectById("purchase"))
        .thenReturn(transaction("purchase", "LOAN_PURCHASE", "loan"));

    assertThrows(
        BusinessException.class,
        () ->
            service.generate(
                command(
                    "loan",
                    "purchase",
                    LoanRepaymentType.ZERO_INTEREST,
                    2,
                    LocalDate.of(2026, 9, 30))));
    verify(planMapper, never()).insert(any(InstallmentPlan.class));
  }

  @Test
  void existingPlanForSameFundingFactIsRejectedBeforeAnyNewRowsAreWritten() {
    LoanProfile profile = profile("loan", "DIRECT_FINANCING", "FIXED", "0");
    when(profileMapper.selectById("loan")).thenReturn(profile);
    when(transactionMapper.selectById("purchase"))
        .thenReturn(transaction("purchase", "LOAN_PURCHASE", "loan"));
    when(planMapper.selectCount(any())).thenReturn(1L);

    assertThrows(
        BusinessException.class,
        () ->
            service.generate(
                command(
                    "loan",
                    "purchase",
                    LoanRepaymentType.ZERO_INTEREST,
                    2,
                    LocalDate.of(2026, 9, 30))));
    verify(entryMapper, never()).selectList(any());
    verify(planMapper, never()).insert(any(InstallmentPlan.class));
  }

@Test
void removeUnpaidPlanDeletesDerivedScheduleGraph() {
  InstallmentPlan plan = new InstallmentPlan();
  plan.setInstallmentPlanId("plan");
  plan.setLiabilityAccountId("loan");
  plan.setPaymentTypeCode("LOAN");

  PaymentSchedule schedule = new PaymentSchedule();
  schedule.setScheduleId("schedule");
  schedule.setInstallmentPlanId("plan");

  when(planMapper.selectById("plan")).thenReturn(plan);
  when(scheduleMapper.selectList(any())).thenReturn(List.of(schedule));
  when(loanPaymentAllocationMapper.selectCount(any())).thenReturn(0L);
  when(planMapper.deleteById("plan")).thenReturn(1);

  assertEquals(1, service.removePlan("plan"));

  verify(loanLifecycleDeletionGuard).assertLoanAccountDeletable("loan");
  verify(scheduleAdjustmentMapper).delete(any());
  verify(detailMapper).delete(any());
  verify(scheduleMapper).delete(any());
  verify(planMapper).deleteById("plan");
}

@Test
void removePlanRejectsHistoricalLoanBeforeDeletingScheduleRows() {
  InstallmentPlan plan = new InstallmentPlan();
  plan.setInstallmentPlanId("plan");
  plan.setLiabilityAccountId("loan");
  plan.setPaymentTypeCode("LOAN");
  when(planMapper.selectById("plan")).thenReturn(plan);
  org.mockito.Mockito.doThrow(new BusinessException(
          com.jiajunxie.common.exception.CommonErrorCode.DATA_CONFLICT, "history"))
      .when(loanLifecycleDeletionGuard).assertLoanAccountDeletable("loan");

  assertThrows(BusinessException.class, () -> service.removePlan("plan"));

  verify(scheduleMapper, never()).delete(any());
  verify(planMapper, never()).deleteById("plan");
}

  private LoanScheduleGenerationCommand command(
      String accountId,
      String sourceId,
      LoanRepaymentType type,
      int count,
      LocalDate firstDueDate) {
    return new LoanScheduleGenerationCommand(
        accountId,
        sourceId,
        type,
        count,
        0,
        firstDueDate,
        LoanRemainderRule.ROUND_TO_LAST,
        LoanScheduleAdjustmentMode.ADJUST_LAST,
        List.of());
  }

  private LoanProfile profile(
      String accountId, String fundingMode, String rateType, String annualRate) {
    LoanProfile profile = new LoanProfile();
    profile.setAccountId(accountId);
    profile.setFundingModeCode(fundingMode);
    profile.setCurrency("JPY");
    profile.setRateTypeCode(rateType);
    profile.setAnnualRate(new BigDecimal(annualRate));
    profile.setPaymentFrequencyCode("MONTHLY");
    profile.setPaymentDay(31);
    profile.setStartDate(LocalDate.of(2026, 8, 1));
    profile.setBusinessDayAdjustmentCode("FOLLOWING");
    profile.setCalendarCode("JP");
    return profile;
  }

  private Transaction transaction(String id, String type, String accountId) {
    Transaction tx = new Transaction();
    tx.setTransactionId(id);
    tx.setTransactionType(type);
    tx.setOriginCode("LOAN_ENGINE");
    tx.setOriginReferenceId(accountId);
    return tx;
  }

  private TransactionEntry entry(
      String transactionId, String accountId, String amount) {
    TransactionEntry entry = new TransactionEntry();
    entry.setTransactionId(transactionId);
    entry.setAccountId(accountId);
    entry.setAmount(new BigDecimal(amount));
    entry.setCurrency("JPY");
    return entry;
  }

  private LoanRateHistory rate(String accountId, LocalDate effectiveDate, String annualRate) {
    LoanRateHistory history = new LoanRateHistory();
    history.setAccountId(accountId);
    history.setEffectiveDate(effectiveDate);
    history.setAnnualRate(new BigDecimal(annualRate));
    return history;
  }
}
