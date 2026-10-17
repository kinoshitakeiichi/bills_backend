package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jiajunxie.domain.loan.LoanRecastExecutionCommand;
import com.jiajunxie.domain.loan.LoanRecastPreviewResult;
import com.jiajunxie.domain.loan.LoanRecastStrategy;
import com.jiajunxie.domain.loan.LoanScheduleItem;
import com.jiajunxie.domain.loan.LoanSchedulePlanView;
import com.jiajunxie.entity.InstallmentPlan;
import com.jiajunxie.entity.LoanRepaymentScheduleDetail;
import com.jiajunxie.entity.PaymentSchedule;
import com.jiajunxie.entity.ScheduleAdjustment;
import com.jiajunxie.mapper.InstallmentPlanMapper;
import com.jiajunxie.mapper.LoanRepaymentScheduleDetailMapper;
import com.jiajunxie.mapper.PaymentScheduleMapper;
import com.jiajunxie.mapper.ScheduleAdjustmentMapper;
import com.jiajunxie.service.LoanRecastPreviewService;
import com.jiajunxie.service.LoanScheduleQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LoanRecastExecutionServiceImplTest {
  private LoanScheduleQueryService queryService;
  private LoanRecastPreviewService previewService;
  private InstallmentPlanMapper planMapper;
  private PaymentScheduleMapper scheduleMapper;
  private LoanRepaymentScheduleDetailMapper detailMapper;
  private ScheduleAdjustmentMapper adjustmentMapper;
  private LoanRecastExecutionServiceImpl service;

  @BeforeEach
  void setUp() {
    queryService = mock(LoanScheduleQueryService.class);
    previewService = mock(LoanRecastPreviewService.class);
    planMapper = mock(InstallmentPlanMapper.class);
    scheduleMapper = mock(PaymentScheduleMapper.class);
    detailMapper = mock(LoanRepaymentScheduleDetailMapper.class);
    adjustmentMapper = mock(ScheduleAdjustmentMapper.class);

    service =
        new LoanRecastExecutionServiceImpl(
            queryService,
            previewService,
            new LoanRepaymentCalculatorImpl(),
            planMapper,
            scheduleMapper,
            detailMapper,
            adjustmentMapper);

    when(scheduleMapper.insert(any(PaymentSchedule.class))).thenReturn(1);
    when(scheduleMapper.updateById(any(PaymentSchedule.class))).thenReturn(1);
    when(detailMapper.insert(any(LoanRepaymentScheduleDetail.class))).thenReturn(1);
    when(adjustmentMapper.insert(any(ScheduleAdjustment.class))).thenReturn(1);
    when(planMapper.updateById(any(InstallmentPlan.class))).thenReturn(1);
  }

  @Test
  void reducePaymentCancelsOldFutureAndCreatesNextGenerationWithSameTerm() {
    LoanSchedulePlanView view = planView();
    when(queryService.getByPlanId(eq("plan"), any(LocalDate.class))).thenReturn(view);
    when(previewService.preview(any()))
        .thenReturn(
            new LoanRecastPreviewResult(
                "plan",
                2,
                LoanRecastStrategy.REDUCE_PAYMENT,
                new BigDecimal("50000"),
                2,
                2,
                new BigDecimal("41000"),
                new BigDecimal("26000"),
                false));

    PaymentSchedule old2 = oldSchedule("old2", 2, "PENDING");
    PaymentSchedule old3 = oldSchedule("old3", 3, "PENDING");
    when(scheduleMapper.selectList(any())).thenReturn(List.of(old2, old3));

    InstallmentPlan plan = plan();
    when(planMapper.selectById("plan")).thenReturn(plan);

    var result =
        service.execute(
            new LoanRecastExecutionCommand(
                "plan",
                2,
                new BigDecimal("50000"),
                LoanRecastStrategy.REDUCE_PAYMENT,
                "提前还款后降低月供"));

    assertEquals(2, result.generationNo());
    assertEquals(2, result.cancelledScheduleCount());
    assertEquals(2, result.createdScheduleCount());
    assertEquals(3, result.newInstallmentCount());
    assertEquals("CANCELLED", old2.getStatusCode());
    assertEquals("CANCELLED", old3.getStatusCode());

    ArgumentCaptor<PaymentSchedule> created = ArgumentCaptor.forClass(PaymentSchedule.class);
    verify(scheduleMapper, times(2)).insert(created.capture());
    assertEquals(2, created.getAllValues().get(0).getInstallmentNo());
    assertEquals(3, created.getAllValues().get(1).getInstallmentNo());
    assertEquals(3, created.getAllValues().get(0).getInstallmentCount());
    assertEquals("PENDING", created.getAllValues().get(0).getStatusCode());

    ArgumentCaptor<LoanRepaymentScheduleDetail> details =
        ArgumentCaptor.forClass(LoanRepaymentScheduleDetail.class);
    verify(detailMapper, times(2)).insert(details.capture());
    assertEquals(2, details.getAllValues().get(0).getGenerationNo());
    assertEquals(0, details.getAllValues().get(1).getRemainingPrincipalAfter().signum());

    verify(adjustmentMapper, times(2)).insert(any(ScheduleAdjustment.class));
    assertEquals(3, plan.getInstallmentCount());
  }

  @Test
  void reduceTermCreatesFewerFutureRowsAndUpdatesPlanInstallmentCount() {
    LoanSchedulePlanView view = planView();
    when(queryService.getByPlanId(eq("plan"), any(LocalDate.class))).thenReturn(view);
    when(previewService.preview(any()))
        .thenReturn(
            new LoanRecastPreviewResult(
                "plan",
                2,
                LoanRecastStrategy.REDUCE_TERM,
                new BigDecimal("30000"),
                2,
                1,
                new BigDecimal("41000"),
                new BigDecimal("30300"),
                false));

    PaymentSchedule old2 = oldSchedule("old2", 2, "PENDING");
    PaymentSchedule old3 = oldSchedule("old3", 3, "PENDING");
    when(scheduleMapper.selectList(any())).thenReturn(List.of(old2, old3));

    InstallmentPlan plan = plan();
    when(planMapper.selectById("plan")).thenReturn(plan);

    var result =
        service.execute(
            new LoanRecastExecutionCommand(
                "plan",
                2,
                new BigDecimal("30000"),
                LoanRecastStrategy.REDUCE_TERM,
                "提前还款后缩短期限"));

    assertEquals(1, result.createdScheduleCount());
    assertEquals(2, result.newInstallmentCount());

    ArgumentCaptor<PaymentSchedule> created = ArgumentCaptor.forClass(PaymentSchedule.class);
    verify(scheduleMapper).insert(created.capture());
    assertEquals(2, created.getValue().getInstallmentNo());
    assertEquals(2, created.getValue().getInstallmentCount());
    assertEquals(2, plan.getInstallmentCount());
  }

  @Test
  void executionAbortsIfFutureScheduleChangedAfterPreview() {
    LoanSchedulePlanView view = planView();
    when(queryService.getByPlanId(eq("plan"), any(LocalDate.class))).thenReturn(view);
    when(previewService.preview(any()))
        .thenReturn(
            new LoanRecastPreviewResult(
                "plan",
                2,
                LoanRecastStrategy.REDUCE_PAYMENT,
                new BigDecimal("50000"),
                2,
                2,
                new BigDecimal("41000"),
                new BigDecimal("26000"),
                false));

    PaymentSchedule old2 = oldSchedule("old2", 2, "PARTIALLY_PAID");
    PaymentSchedule old3 = oldSchedule("old3", 3, "PENDING");
    when(scheduleMapper.selectList(any())).thenReturn(List.of(old2, old3));

    assertThrows(
        com.jiajunxie.common.exception.BusinessException.class,
        () ->
            service.execute(
                new LoanRecastExecutionCommand(
                    "plan",
                    2,
                    new BigDecimal("50000"),
                    LoanRecastStrategy.REDUCE_PAYMENT,
                    "并发状态变化")));

    verify(scheduleMapper, never()).insert(any(PaymentSchedule.class));
    verify(adjustmentMapper, never()).insert(any(ScheduleAdjustment.class));
  }

  private LoanSchedulePlanView planView() {
    return new LoanSchedulePlanView(
        "plan",
        "funding",
        "loan",
        "JPY",
        new BigDecimal("120000"),
        new BigDecimal("120000"),
        new BigDecimal("2400"),
        BigDecimal.ZERO,
        List.of(
            item(1, "PAID", "40000", "1200", "80000", 1),
            item(2, "PENDING", "40000", "800", "40000", 1),
            item(3, "PENDING", "40000", "400", "0", 1)));
  }

  private LoanScheduleItem item(
      int no,
      String status,
      String principal,
      String interest,
      String remaining,
      int generationNo) {
    BigDecimal p = new BigDecimal(principal);
    BigDecimal i = new BigDecimal(interest);
    LocalDate date = LocalDate.of(2026, 8 + no, 10);
    return new LoanScheduleItem(
        "s" + no,
        "plan",
        "funding",
        "loan",
        no,
        3,
        "EQUAL_PRINCIPAL",
        p,
        i,
        BigDecimal.ZERO,
        p,
        i,
        BigDecimal.ZERO,
        p.add(i),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        p,
        i,
        BigDecimal.ZERO,
        p.add(i),
        new BigDecimal(remaining),
        new BigDecimal("0.12"),
        LocalDate.of(2026, 1, 1),
        date,
        date,
        null,
        date,
        status,
        status,
        "ADJUST_LAST",
        "LOAN_SCHEDULE_V1",
        0,
        generationNo);
  }

  private PaymentSchedule oldSchedule(String id, int no, String status) {
    PaymentSchedule row = new PaymentSchedule();
    row.setScheduleId(id);
    row.setInstallmentPlanId("plan");
    row.setInstallmentNo(no);
    row.setInstallmentCount(3);
    row.setStatusCode(status);
    return row;
  }

  private InstallmentPlan plan() {
    InstallmentPlan plan = new InstallmentPlan();
    plan.setInstallmentPlanId("plan");
    plan.setInstallmentCount(3);
    plan.setStatusCode("ACTIVE");
    return plan;
  }
}
