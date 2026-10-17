package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.domain.loan.LoanScheduleAmountAdjustmentCommand;
import com.jiajunxie.domain.loan.LoanScheduleAmountField;
import com.jiajunxie.domain.loan.LoanScheduleDateAdjustmentCommand;
import com.jiajunxie.entity.LoanRepaymentScheduleDetail;
import com.jiajunxie.entity.PaymentSchedule;
import com.jiajunxie.entity.ScheduleAdjustment;
import com.jiajunxie.mapper.LoanRepaymentScheduleDetailMapper;
import com.jiajunxie.mapper.PaymentScheduleMapper;
import com.jiajunxie.mapper.ScheduleAdjustmentMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LoanScheduleAdjustmentServiceImplTest {
  private PaymentScheduleMapper scheduleMapper;
  private LoanRepaymentScheduleDetailMapper detailMapper;
  private ScheduleAdjustmentMapper adjustmentMapper;
  private LoanScheduleAdjustmentServiceImpl service;

  @BeforeEach
  void setUp() {
    scheduleMapper = mock(PaymentScheduleMapper.class);
    detailMapper = mock(LoanRepaymentScheduleDetailMapper.class);
    adjustmentMapper = mock(ScheduleAdjustmentMapper.class);
    service = new LoanScheduleAdjustmentServiceImpl(scheduleMapper, detailMapper, adjustmentMapper);
    when(detailMapper.updateById(any(LoanRepaymentScheduleDetail.class))).thenReturn(1);
    when(scheduleMapper.updateById(any(PaymentSchedule.class))).thenReturn(1);
    when(adjustmentMapper.insert(any(ScheduleAdjustment.class))).thenReturn(1);
  }

  @Test
  void adjustLastMovesPrincipalDeltaToFinalPendingInstallmentAndAuditsBothRows() {
    PaymentSchedule first = schedule("s1", 1, "30000");
    PaymentSchedule last = schedule("s2", 2, "70000");
    LoanRepaymentScheduleDetail d1 = detail("s1", "ADJUST_LAST");
    LoanRepaymentScheduleDetail d2 = detail("s2", "ADJUST_LAST");

    when(scheduleMapper.selectById("s1")).thenReturn(first);
    when(detailMapper.selectById("s1")).thenReturn(d1);
    when(detailMapper.selectById("s2")).thenReturn(d2);
    when(scheduleMapper.selectList(any())).thenReturn(List.of(first, last));

    service.adjustAmount(
        new LoanScheduleAmountAdjustmentCommand(
            "s1", LoanScheduleAmountField.PRINCIPAL, new BigDecimal("35000"), "用户调整"));

    assertEquals(new BigDecimal("35000"), d1.getManualPrincipalAmount());
    assertEquals(new BigDecimal("65000"), d2.getManualPrincipalAmount());

    ArgumentCaptor<ScheduleAdjustment> audits = ArgumentCaptor.forClass(ScheduleAdjustment.class);
    verify(adjustmentMapper, times(2)).insert(audits.capture());
    assertEquals("LOAN_PRINCIPAL", audits.getAllValues().get(0).getAdjustmentTypeCode());
    assertEquals(
        "LOAN_PRINCIPAL_AUTO_REMAINDER",
        audits.getAllValues().get(1).getAdjustmentTypeCode());
  }

  @Test
  void keepRemainingChangesInterestWithoutTouchingSystemSnapshot() {
    PaymentSchedule first = schedule("s1", 1, "30000");
    LoanRepaymentScheduleDetail detail = detail("s1", "KEEP_REMAINING");
    detail.setScheduledInterestAmount(new BigDecimal("500"));
    when(scheduleMapper.selectById("s1")).thenReturn(first);
    when(detailMapper.selectById("s1")).thenReturn(detail);

    service.adjustAmount(
        new LoanScheduleAmountAdjustmentCommand(
            "s1", LoanScheduleAmountField.INTEREST, new BigDecimal("650"), "银行通知"));

    assertEquals(new BigDecimal("500"), detail.getScheduledInterestAmount());
    assertEquals(new BigDecimal("650"), detail.getManualInterestAmount());
    verify(scheduleMapper, never()).updateById(any(PaymentSchedule.class));
  }

  @Test
  void revertingToSystemAmountClearsManualOverride() {
    PaymentSchedule first = schedule("s1", 1, "30000");
    LoanRepaymentScheduleDetail detail = detail("s1", "KEEP_REMAINING");
    detail.setManualFeeAmount(new BigDecimal("300"));
    first.setScheduledFeeAmount(new BigDecimal("100"));
    when(scheduleMapper.selectById("s1")).thenReturn(first);
    when(detailMapper.selectById("s1")).thenReturn(detail);

    service.adjustAmount(
        new LoanScheduleAmountAdjustmentCommand(
            "s1", LoanScheduleAmountField.FEE, new BigDecimal("100"), "恢复系统值"));

    assertNull(detail.getManualFeeAmount());
  }

  @Test
  void dateAdjustmentStoresManualDateAndEffectiveBeforeAfterAudit() {
    PaymentSchedule first = schedule("s1", 1, "30000");
    first.setCalculatedPaymentDate(LocalDate.of(2026, 9, 10));
    when(scheduleMapper.selectById("s1")).thenReturn(first);

    service.adjustDate(
        new LoanScheduleDateAdjustmentCommand(
            "s1", LocalDate.of(2026, 9, 15), "银行约定"));

    assertEquals(LocalDate.of(2026, 9, 15), first.getManualPaymentDate());
    ArgumentCaptor<ScheduleAdjustment> audit = ArgumentCaptor.forClass(ScheduleAdjustment.class);
    verify(adjustmentMapper).insert(audit.capture());
    assertEquals(LocalDate.of(2026, 9, 10), audit.getValue().getBeforeDate());
    assertEquals(LocalDate.of(2026, 9, 15), audit.getValue().getAfterDate());
  }

  @Test
  void partiallyPaidOrPaidScheduleCannotBeAdjusted() {
    PaymentSchedule first = schedule("s1", 1, "30000");
    first.setStatusCode("PARTIALLY_PAID");
    when(scheduleMapper.selectById("s1")).thenReturn(first);

    assertThrows(
        BusinessException.class,
        () ->
            service.adjustDate(
                new LoanScheduleDateAdjustmentCommand(
                    "s1", LocalDate.of(2026, 9, 15), "不应允许")));
    verify(adjustmentMapper, never()).insert(any(ScheduleAdjustment.class));
  }

  private PaymentSchedule schedule(String id, int no, String principal) {
    PaymentSchedule schedule = new PaymentSchedule();
    schedule.setScheduleId(id);
    schedule.setInstallmentPlanId("plan");
    schedule.setInstallmentNo(no);
    schedule.setInstallmentCount(2);
    schedule.setPaymentTypeCode("LOAN");
    schedule.setStatusCode("PENDING");
    schedule.setScheduledPrincipalAmount(new BigDecimal(principal));
    schedule.setScheduledFeeAmount(BigDecimal.ZERO);
    return schedule;
  }

  private LoanRepaymentScheduleDetail detail(String id, String mode) {
    LoanRepaymentScheduleDetail detail = new LoanRepaymentScheduleDetail();
    detail.setScheduleId(id);
    detail.setScheduledInterestAmount(BigDecimal.ZERO);
    detail.setAdjustmentModeCode(mode);
    return detail;
  }
}
