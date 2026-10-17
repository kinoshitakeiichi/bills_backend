package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jiajunxie.domain.loan.LoanPaymentAllocationResult;
import com.jiajunxie.entity.InstallmentPlan;
import com.jiajunxie.entity.LoanPaymentAllocation;
import com.jiajunxie.entity.LoanPaymentDetail;
import com.jiajunxie.entity.LoanProfile;
import com.jiajunxie.entity.LoanRepaymentScheduleDetail;
import com.jiajunxie.entity.PaymentSchedule;
import com.jiajunxie.mapper.InstallmentPlanMapper;
import com.jiajunxie.mapper.LoanPaymentAllocationMapper;
import com.jiajunxie.mapper.LoanPaymentDetailMapper;
import com.jiajunxie.mapper.LoanProfileMapper;
import com.jiajunxie.mapper.LoanRepaymentScheduleDetailMapper;
import com.jiajunxie.mapper.PaymentScheduleMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LoanPaymentAllocationServiceImplTest {
  private LoanPaymentDetailMapper paymentDetailMapper;
  private InstallmentPlanMapper planMapper;
  private PaymentScheduleMapper scheduleMapper;
  private LoanRepaymentScheduleDetailMapper scheduleDetailMapper;
  private LoanPaymentAllocationMapper allocationMapper;
  private LoanProfileMapper loanProfileMapper;
  private LoanPaymentAllocationServiceImpl service;

  @BeforeEach
  void setUp() {
    paymentDetailMapper = mock(LoanPaymentDetailMapper.class);
    planMapper = mock(InstallmentPlanMapper.class);
    scheduleMapper = mock(PaymentScheduleMapper.class);
    scheduleDetailMapper = mock(LoanRepaymentScheduleDetailMapper.class);
    allocationMapper = mock(LoanPaymentAllocationMapper.class);
    loanProfileMapper = mock(LoanProfileMapper.class);
    service =
        new LoanPaymentAllocationServiceImpl(
            paymentDetailMapper,
            planMapper,
            scheduleMapper,
            scheduleDetailMapper,
            allocationMapper,
            loanProfileMapper);

    when(loanProfileMapper.selectByIdForUpdate(any(String.class))).thenReturn(new LoanProfile());
    when(allocationMapper.insert(any(LoanPaymentAllocation.class))).thenReturn(1);
    when(allocationMapper.updateById(any(LoanPaymentAllocation.class))).thenReturn(1);
    when(scheduleMapper.updateById(any(PaymentSchedule.class))).thenReturn(1);
    when(planMapper.updateById(any(InstallmentPlan.class))).thenReturn(1);
  }

  @Test
  void fifoAllocatesPrincipalInterestAndFeeIndependentlyAndSettlesScheduleStatus() {
    LoanPaymentDetail payment = payment("pay", "50000", "500", "100");
    when(paymentDetailMapper.selectById("pay")).thenReturn(payment);
    when(allocationMapper.selectList(any()))
        .thenReturn(List.of())
        .thenReturn(List.of());

    InstallmentPlan plan = plan("plan");
    when(planMapper.selectList(any())).thenReturn(List.of(plan));

    PaymentSchedule first = schedule("s1", "plan", 1, "30000", "100", LocalDate.of(2026, 9, 10));
    PaymentSchedule second = schedule("s2", "plan", 2, "70000", "0", LocalDate.of(2026, 10, 10));
    when(scheduleMapper.selectList(any()))
        .thenReturn(List.of(first, second))
        .thenReturn(List.of(first, second));
    when(scheduleDetailMapper.selectBatchIds(any()))
        .thenReturn(List.of(detail("s1", "300"), detail("s2", "700")));

    LoanPaymentAllocationResult result = service.allocate("pay");

    assertEquals(new BigDecimal("50000"), result.allocatedPrincipalAmount());
    assertEquals(new BigDecimal("500"), result.allocatedInterestAmount());
    assertEquals(new BigDecimal("100"), result.allocatedFeeAmount());
    assertEquals(0, result.unallocatedPrincipalAmount().signum());
    assertEquals(2, result.touchedScheduleCount());

    ArgumentCaptor<LoanPaymentAllocation> captor =
        ArgumentCaptor.forClass(LoanPaymentAllocation.class);
    verify(allocationMapper, times(2)).insert(captor.capture());
    assertEquals("s1", captor.getAllValues().get(0).getScheduleId());
    assertEquals(new BigDecimal("30000"), captor.getAllValues().get(0).getAllocatedPrincipalAmount());
    assertEquals(new BigDecimal("300"), captor.getAllValues().get(0).getAllocatedInterestAmount());
    assertEquals(new BigDecimal("100"), captor.getAllValues().get(0).getAllocatedFeeAmount());

    assertEquals("PAID", first.getStatusCode());
    assertEquals("PARTIALLY_PAID", second.getStatusCode());
    assertEquals("ACTIVE", plan.getStatusCode());
  }

  @Test
  void extraPrincipalRemainsUnallocatedAndDoesNotRewritePaymentFact() {
    LoanPaymentDetail payment = payment("pay", "80000", "300", "0");
    when(paymentDetailMapper.selectById("pay")).thenReturn(payment);
    when(allocationMapper.selectList(any()))
        .thenReturn(List.of())
        .thenReturn(List.of());

    InstallmentPlan plan = plan("plan");
    when(planMapper.selectList(any())).thenReturn(List.of(plan));
    PaymentSchedule only = schedule("s1", "plan", 1, "30000", "0", LocalDate.of(2026, 9, 10));
    when(scheduleMapper.selectList(any()))
        .thenReturn(List.of(only))
        .thenReturn(List.of(only));
    when(scheduleDetailMapper.selectBatchIds(any()))
        .thenReturn(List.of(detail("s1", "300")));

    LoanPaymentAllocationResult result = service.allocate("pay");

    assertEquals(new BigDecimal("30000"), result.allocatedPrincipalAmount());
    assertEquals(new BigDecimal("50000"), result.unallocatedPrincipalAmount());
    assertEquals(0, result.unallocatedInterestAmount().signum());
    assertEquals("PAID", only.getStatusCode());
    assertEquals("COMPLETED", plan.getStatusCode());
    verify(paymentDetailMapper, never()).updateById(any(LoanPaymentDetail.class));
  }

  @Test
  void paymentWithoutAnyPlanStaysFullyUnallocatedButRemainsValid() {
    LoanPaymentDetail payment = payment("pay", "20000", "500", "110");
    when(paymentDetailMapper.selectById("pay")).thenReturn(payment);
    when(allocationMapper.selectList(any())).thenReturn(List.of());
    when(planMapper.selectList(any())).thenReturn(List.of());

    LoanPaymentAllocationResult result = service.allocate("pay");

    assertEquals(new BigDecimal("20000"), result.unallocatedPrincipalAmount());
    assertEquals(new BigDecimal("500"), result.unallocatedInterestAmount());
    assertEquals(new BigDecimal("110"), result.unallocatedFeeAmount());
    assertEquals(0, result.touchedScheduleCount());
    verify(allocationMapper, never()).insert(any(LoanPaymentAllocation.class));
  }

  @Test
  void retryContinuesFromExistingAllocationInsteadOfDuplicatingIt() {
    LoanPaymentDetail payment = payment("pay", "50000", "0", "0");
    when(paymentDetailMapper.selectById("pay")).thenReturn(payment);

    LoanPaymentAllocation existing = new LoanPaymentAllocation();
    existing.setAllocationId("a1");
    existing.setLoanPaymentTransactionId("pay");
    existing.setScheduleId("s1");
    existing.setAllocatedPrincipalAmount(new BigDecimal("10000"));
    existing.setAllocatedInterestAmount(BigDecimal.ZERO);
    existing.setAllocatedFeeAmount(BigDecimal.ZERO);

    when(allocationMapper.selectList(any()))
        .thenReturn(List.of(existing))
        .thenReturn(List.of(existing));

    InstallmentPlan plan = plan("plan");
    when(planMapper.selectList(any())).thenReturn(List.of(plan));
    PaymentSchedule first = schedule("s1", "plan", 1, "30000", "0", LocalDate.of(2026, 9, 10));
    PaymentSchedule second = schedule("s2", "plan", 2, "70000", "0", LocalDate.of(2026, 10, 10));
    when(scheduleMapper.selectList(any()))
        .thenReturn(List.of(first, second))
        .thenReturn(List.of(first, second));
    when(scheduleDetailMapper.selectBatchIds(any()))
        .thenReturn(List.of(detail("s1", "0"), detail("s2", "0")));

    LoanPaymentAllocationResult result = service.allocate("pay");

    assertEquals(new BigDecimal("50000"), result.allocatedPrincipalAmount());
    assertEquals(0, result.unallocatedPrincipalAmount().signum());
    assertEquals(new BigDecimal("30000"), existing.getAllocatedPrincipalAmount());
    verify(allocationMapper).updateById(existing);

    ArgumentCaptor<LoanPaymentAllocation> inserted =
        ArgumentCaptor.forClass(LoanPaymentAllocation.class);
    verify(allocationMapper).insert(inserted.capture());
    assertEquals("s2", inserted.getValue().getScheduleId());
    assertEquals(new BigDecimal("20000"), inserted.getValue().getAllocatedPrincipalAmount());
  }

  @Test
  void reverseRemovesOwnAllocationAndRestoresScheduleAndPlanStatus() {
    LoanPaymentAllocation own = new LoanPaymentAllocation();
    own.setAllocationId("a1");
    own.setLoanPaymentTransactionId("pay");
    own.setScheduleId("s1");
    own.setAllocatedPrincipalAmount(new BigDecimal("30000"));
    own.setAllocatedInterestAmount(new BigDecimal("300"));
    own.setAllocatedFeeAmount(new BigDecimal("100"));

    when(allocationMapper.selectList(any()))
        .thenReturn(List.of(own))
        .thenReturn(List.of());
    when(allocationMapper.delete(any())).thenReturn(1);

    PaymentSchedule schedule = schedule("s1", "plan", 1, "30000", "100", LocalDate.of(2026, 9, 10));
    schedule.setStatusCode("PAID");
    when(scheduleMapper.selectBatchIds(any())).thenReturn(List.of(schedule));
    when(scheduleDetailMapper.selectBatchIds(any())).thenReturn(List.of(detail("s1", "300")));
    when(scheduleMapper.selectList(any())).thenReturn(List.of(schedule));

    InstallmentPlan plan = plan("plan");
    plan.setStatusCode("COMPLETED");
    when(planMapper.selectById("plan")).thenReturn(plan);

    service.reverse("pay");

    assertEquals("PENDING", schedule.getStatusCode());
    assertEquals("ACTIVE", plan.getStatusCode());
    verify(allocationMapper).delete(any());
    verify(scheduleMapper).updateById(schedule);
    verify(planMapper).updateById(plan);
  }

  @Test
  void reverseWithoutAllocationIsSafeNoop() {
    when(allocationMapper.selectList(any())).thenReturn(List.of());

    assertDoesNotThrow(() -> service.reverse("pay"));

    verify(allocationMapper, never()).delete(any());
    verifyNoInteractions(scheduleDetailMapper);
  }

  private LoanPaymentDetail payment(
      String id, String principal, String interest, String fee) {
    LoanPaymentDetail detail = new LoanPaymentDetail();
    detail.setTransactionId(id);
    detail.setLoanAccountId("loan");
    detail.setPrincipalAmount(new BigDecimal(principal));
    detail.setInterestAmount(new BigDecimal(interest));
    detail.setFeeAmount(new BigDecimal(fee));
    detail.setCurrency("JPY");
    return detail;
  }

  private InstallmentPlan plan(String id) {
    InstallmentPlan plan = new InstallmentPlan();
    plan.setInstallmentPlanId(id);
    plan.setLiabilityAccountId("loan");
    plan.setPaymentTypeCode("LOAN");
    plan.setStatusCode("ACTIVE");
    return plan;
  }

  private PaymentSchedule schedule(
      String id, String planId, int no, String principal, String fee, LocalDate dueDate) {
    PaymentSchedule schedule = new PaymentSchedule();
    schedule.setScheduleId(id);
    schedule.setInstallmentPlanId(planId);
    schedule.setInstallmentNo(no);
    schedule.setInstallmentCount(2);
    schedule.setPaymentTypeCode("LOAN");
    schedule.setScheduledPrincipalAmount(new BigDecimal(principal));
    schedule.setScheduledFeeAmount(new BigDecimal(fee));
    schedule.setScheduledPaymentDate(dueDate);
    schedule.setCalculatedPaymentDate(dueDate);
    schedule.setStatusCode("PENDING");
    return schedule;
  }

  private LoanRepaymentScheduleDetail detail(String scheduleId, String interest) {
    LoanRepaymentScheduleDetail detail = new LoanRepaymentScheduleDetail();
    detail.setScheduleId(scheduleId);
    detail.setScheduledInterestAmount(new BigDecimal(interest));
    return detail;
  }
}
