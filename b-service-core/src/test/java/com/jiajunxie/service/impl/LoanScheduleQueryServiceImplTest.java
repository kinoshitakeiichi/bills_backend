package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jiajunxie.entity.InstallmentPlan;
import com.jiajunxie.entity.LoanRepaymentScheduleDetail;
import com.jiajunxie.entity.PaymentSchedule;
import com.jiajunxie.mapper.InstallmentPlanMapper;
import com.jiajunxie.mapper.LoanRepaymentScheduleDetailMapper;
import com.jiajunxie.mapper.LoanPaymentAllocationMapper;
import com.jiajunxie.mapper.PaymentScheduleMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoanScheduleQueryServiceImplTest {

  @Test
  void queryPreservesSystemValuesAndExposesManualEffectiveValuesAndDerivedOverdue() {
    InstallmentPlanMapper planMapper = mock(InstallmentPlanMapper.class);
    PaymentScheduleMapper scheduleMapper = mock(PaymentScheduleMapper.class);
    LoanRepaymentScheduleDetailMapper detailMapper = mock(LoanRepaymentScheduleDetailMapper.class);
    LoanPaymentAllocationMapper allocationMapper = mock(LoanPaymentAllocationMapper.class);
    var service =
        new LoanScheduleQueryServiceImpl(
            planMapper, scheduleMapper, detailMapper, allocationMapper);

    InstallmentPlan plan = plan();
    when(planMapper.selectById("plan")).thenReturn(plan);

    PaymentSchedule first = schedule("s1", 1, "30000", "100", LocalDate.of(2026, 8, 10));
    PaymentSchedule second = schedule("s2", 2, "70000", "0", LocalDate.of(2026, 9, 10));
    second.setManualPaymentDate(LocalDate.of(2026, 9, 15));
    when(scheduleMapper.selectList(any())).thenReturn(List.of(first, second));

    LoanRepaymentScheduleDetail d1 = detail("s1", "500", "70000");
    d1.setManualPrincipalAmount(new BigDecimal("25000"));
    d1.setManualInterestAmount(new BigDecimal("600"));
    LoanRepaymentScheduleDetail d2 = detail("s2", "200", "0");
    d2.setManualFeeAmount(new BigDecimal("50"));
    when(detailMapper.selectBatchIds(any())).thenReturn(List.of(d1, d2));

    var allocation = new com.jiajunxie.entity.LoanPaymentAllocation();
    allocation.setScheduleId("s1");
    allocation.setAllocatedPrincipalAmount(new BigDecimal("10000"));
    allocation.setAllocatedInterestAmount(new BigDecimal("100"));
    allocation.setAllocatedFeeAmount(BigDecimal.ZERO);
    when(allocationMapper.selectList(any())).thenReturn(List.of(allocation));

    var view = service.getByPlanId("plan", LocalDate.of(2026, 8, 11));

    assertEquals(new BigDecimal("30000"), view.schedules().get(0).systemPrincipalAmount());
    assertEquals(new BigDecimal("25000"), view.schedules().get(0).effectivePrincipalAmount());
    assertEquals(new BigDecimal("600"), view.schedules().get(0).effectiveInterestAmount());
    assertEquals(new BigDecimal("25700"), view.schedules().get(0).effectiveTotalAmount());
    assertEquals(new BigDecimal("10000"), view.schedules().get(0).allocatedPrincipalAmount());
    assertEquals(new BigDecimal("100"), view.schedules().get(0).allocatedInterestAmount());
    assertEquals(new BigDecimal("15000"), view.schedules().get(0).outstandingPrincipalAmount());
    assertEquals(new BigDecimal("500"), view.schedules().get(0).outstandingInterestAmount());
    assertEquals(new BigDecimal("15600"), view.schedules().get(0).outstandingTotalAmount());
    assertEquals(new BigDecimal("75000"), view.schedules().get(0).remainingPrincipalAfter());
    assertEquals("OVERDUE", view.schedules().get(0).displayStatusCode());
    assertEquals(LocalDate.of(2026, 9, 15), view.schedules().get(1).effectivePaymentDate());
    assertEquals(new BigDecimal("95000"), view.effectiveTotalPrincipal());
    assertEquals(new BigDecimal("800"), view.effectiveTotalInterest());
    assertEquals(new BigDecimal("150"), view.effectiveTotalFee());
  }

  private InstallmentPlan plan() {
    InstallmentPlan plan = new InstallmentPlan();
    plan.setInstallmentPlanId("plan");
    plan.setSourceTransactionId("funding");
    plan.setLiabilityAccountId("loan");
    plan.setPaymentTypeCode("LOAN");
    plan.setCurrency("JPY");
    plan.setPrincipalAmount(new BigDecimal("100000"));
    return plan;
  }

  private PaymentSchedule schedule(
      String id, int no, String principal, String fee, LocalDate date) {
    PaymentSchedule schedule = new PaymentSchedule();
    schedule.setScheduleId(id);
    schedule.setInstallmentPlanId("plan");
    schedule.setSourceTransactionId("funding");
    schedule.setLiabilityAccountId("loan");
    schedule.setInstallmentNo(no);
    schedule.setInstallmentCount(2);
    schedule.setScheduledPrincipalAmount(new BigDecimal(principal));
    schedule.setScheduledFeeAmount(new BigDecimal(fee));
    schedule.setScheduledPaymentDate(date);
    schedule.setCalculatedPaymentDate(date);
    schedule.setPaymentTypeCode("LOAN");
    schedule.setStatusCode("PENDING");
    return schedule;
  }

  private LoanRepaymentScheduleDetail detail(String id, String interest, String remaining) {
    LoanRepaymentScheduleDetail detail = new LoanRepaymentScheduleDetail();
    detail.setScheduleId(id);
    detail.setLoanAccountId("loan");
    detail.setRepaymentTypeCode("MANUAL");
    detail.setScheduledInterestAmount(new BigDecimal(interest));
    detail.setRemainingPrincipalAfter(new BigDecimal(remaining));
    detail.setAnnualRateSnapshot(BigDecimal.ZERO);
    detail.setAdjustmentModeCode("KEEP_REMAINING");
    detail.setCalculationVersion("LOAN_SCHEDULE_V1");
    detail.setAmountScale(0);
    detail.setGenerationNo(1);
    return detail;
  }
}
