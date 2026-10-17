package com.jiajunxie.service.impl;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.domain.loan.LoanRateSnapshotInput;
import com.jiajunxie.domain.loan.LoanRecastExecutionCommand;
import com.jiajunxie.domain.loan.LoanRecastExecutionResult;
import com.jiajunxie.domain.loan.LoanRecastPreviewCommand;
import com.jiajunxie.domain.loan.LoanRepaymentCalculationRequest;
import com.jiajunxie.domain.loan.LoanRepaymentCalculationResult;
import com.jiajunxie.domain.loan.LoanRepaymentType;
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
import com.jiajunxie.service.LoanRecastExecutionService;
import com.jiajunxie.service.LoanRecastPreviewService;
import com.jiajunxie.service.LoanRepaymentCalculator;
import com.jiajunxie.service.LoanScheduleQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoanRecastExecutionServiceImpl implements LoanRecastExecutionService {
  private final LoanScheduleQueryService queryService;
  private final LoanRecastPreviewService previewService;
  private final LoanRepaymentCalculator calculator;
  private final InstallmentPlanMapper planMapper;
  private final PaymentScheduleMapper scheduleMapper;
  private final LoanRepaymentScheduleDetailMapper detailMapper;
  private final ScheduleAdjustmentMapper adjustmentMapper;

  @Override
  @Transactional
  public LoanRecastExecutionResult execute(LoanRecastExecutionCommand command) {
    validate(command);

    LoanSchedulePlanView current =
        queryService.getByPlanId(command.installmentPlanId(), LocalDate.now());
    var preview =
        previewService.preview(
            new LoanRecastPreviewCommand(
                command.installmentPlanId(),
                command.fromInstallmentNo(),
                command.remainingPrincipal(),
                command.strategy()));

    List<LoanScheduleItem> future =
        current.schedules().stream()
            .filter(item -> item.installmentNo() >= command.fromInstallmentNo())
            .toList();

    if (future.isEmpty()) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "没有可重算的未来期次");
    }
    if (!future.stream().allMatch(item -> "PENDING".equals(item.storedStatusCode()))) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "只有连续 PENDING 的未来期次可以执行 Recast");
    }

    int proposedRemaining = preview.proposedRemainingInstallments();
    List<LoanScheduleItem> calculationTimeline = future.subList(0, proposedRemaining);
    LoanRepaymentType repaymentType =
        LoanRepaymentType.valueOf(future.get(0).repaymentTypeCode());

    LoanRepaymentCalculationResult calculation =
        calculator.calculate(
            new LoanRepaymentCalculationRequest(
                command.remainingPrincipal(),
                repaymentType,
                proposedRemaining,
                future.get(0).amountScale(),
                12,
                future.get(0).annualRateSnapshot(),
                buildRelativeRateSnapshots(calculationTimeline),
                List.of()));

    int nextGeneration =
        current.schedules().stream()
                .mapToInt(LoanScheduleItem::generationNo)
                .max()
                .orElse(1)
            + 1;

    Instant now = Instant.now();

    List<PaymentSchedule> oldFutureRows =
        scheduleMapper.selectList(
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<PaymentSchedule>lambdaQuery()
                .eq(PaymentSchedule::getInstallmentPlanId, command.installmentPlanId())
                .ge(PaymentSchedule::getInstallmentNo, command.fromInstallmentNo())
                .ne(PaymentSchedule::getStatusCode, "CANCELLED")
                .orderByAsc(PaymentSchedule::getInstallmentNo));

    if (oldFutureRows.size() != future.size()) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "Recast 执行前计划已发生变化，请重新预览");
    }
    if (!oldFutureRows.stream().allMatch(row -> "PENDING".equals(row.getStatusCode()))) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "Recast 执行前已有期次进入结算状态，请重新预览");
    }

    for (PaymentSchedule old : oldFutureRows) {
      old.setStatusCode("CANCELLED");
      old.setUpdatedAt(now);
      scheduleMapper.updateById(old);

      ScheduleAdjustment audit = new ScheduleAdjustment();
      audit.setAdjustmentId(UUID.randomUUID().toString());
      audit.setScheduleId(old.getScheduleId());
      audit.setAdjustmentTypeCode("LOAN_RECAST_CANCELLED");
      audit.setReason(
          command.strategy().name() + " generation " + nextGeneration + "：" + command.reason().trim());
      audit.setCreatedAt(now);
      adjustmentMapper.insert(audit);
    }

    int newTotalInstallments = command.fromInstallmentNo() - 1 + proposedRemaining;
    List<PaymentSchedule> createdSchedules = new ArrayList<>();

    for (int i = 0; i < proposedRemaining; i++) {
      var installment = calculation.installments().get(i);
      LoanScheduleItem source = calculationTimeline.get(i);

      PaymentSchedule row = new PaymentSchedule();
      String scheduleId = UUID.randomUUID().toString();
      row.setScheduleId(scheduleId);
      row.setInstallmentPlanId(command.installmentPlanId());
      row.setSourceTransactionId(source.sourceTransactionId());
      row.setLiabilityAccountId(source.loanAccountId());
      row.setInstallmentNo(command.fromInstallmentNo() + i);
      row.setInstallmentCount(newTotalInstallments);
      row.setScheduledPrincipalAmount(installment.principalAmount());
      row.setScheduledFeeAmount(source.effectiveFeeAmount());
      row.setAdjustmentAmount(BigDecimal.ZERO.setScale(source.amountScale()));
      row.setCurrency(current.currency());
      row.setPaymentTypeCode("LOAN");
      row.setScheduledBillingDate(source.scheduledPaymentDate());
      row.setScheduledPaymentDate(source.scheduledPaymentDate());
      row.setCalculatedPaymentDate(source.calculatedPaymentDate());
      row.setManualPaymentDate(null);
      row.setStatusCode("PENDING");
      row.setCreatedAt(now);
      row.setUpdatedAt(now);
      scheduleMapper.insert(row);
      createdSchedules.add(row);

      LoanRepaymentScheduleDetail detail = new LoanRepaymentScheduleDetail();
      detail.setScheduleId(scheduleId);
      detail.setLoanAccountId(source.loanAccountId());
      detail.setRepaymentTypeCode(repaymentType.name());
      detail.setScheduledInterestAmount(installment.interestAmount());
      detail.setRemainingPrincipalAfter(installment.remainingPrincipalAfter());
      detail.setAnnualRateSnapshot(installment.annualRateSnapshot());
      detail.setRateEffectiveFrom(installment.rateEffectiveFrom());
      detail.setRemainderRuleCode("ROUND_TO_LAST");
      detail.setAdjustmentModeCode(source.adjustmentModeCode());
      detail.setCalculationVersion(calculation.calculationVersion());
      detail.setAmountScale(source.amountScale());
      detail.setGenerationNo(nextGeneration);
      detail.setCreatedAt(now);
      detailMapper.insert(detail);
    }

    InstallmentPlan plan = planMapper.selectById(command.installmentPlanId());
    if (plan == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "贷款还款计划不存在");
    }
    plan.setInstallmentCount(newTotalInstallments);
    plan.setStatusCode("ACTIVE");
    plan.setUpdatedAt(now);
    planMapper.updateById(plan);

    return new LoanRecastExecutionResult(
        command.installmentPlanId(),
        command.fromInstallmentNo(),
        command.strategy(),
        nextGeneration,
        oldFutureRows.size(),
        createdSchedules.size(),
        newTotalInstallments,
        command.remainingPrincipal(),
        calculation
            .installments()
            .get(0)
            .principalAmount()
            .add(calculation.installments().get(0).interestAmount())
            .add(calculationTimeline.get(0).effectiveFeeAmount()),
        calculation.calculationVersion());
  }

  private void validate(LoanRecastExecutionCommand command) {
    if (command == null
        || command.installmentPlanId() == null
        || command.installmentPlanId().isBlank()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "还款计划不能为空");
    }
    if (command.fromInstallmentNo() < 1) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "重算起始期次必须大于 0");
    }
    if (command.remainingPrincipal() == null || command.remainingPrincipal().signum() <= 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "重算剩余本金必须大于 0");
    }
    if (command.strategy() == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "重算策略不能为空");
    }
    if (command.reason() == null || command.reason().isBlank()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "执行 Recast 必须填写原因");
    }
  }

  private List<LoanRateSnapshotInput> buildRelativeRateSnapshots(
      List<LoanScheduleItem> timeline) {
    List<LoanRateSnapshotInput> snapshots = new ArrayList<>();
    BigDecimal previousRate = timeline.get(0).annualRateSnapshot();
    LocalDate previousEffective = timeline.get(0).rateEffectiveFrom();

    for (int i = 1; i < timeline.size(); i++) {
      LoanScheduleItem item = timeline.get(i);
      if (previousRate.compareTo(item.annualRateSnapshot()) != 0
          || !Objects.equals(previousEffective, item.rateEffectiveFrom())) {
        snapshots.add(
            new LoanRateSnapshotInput(
                i + 1, item.annualRateSnapshot(), item.rateEffectiveFrom()));
        previousRate = item.annualRateSnapshot();
        previousEffective = item.rateEffectiveFrom();
      }
    }
    return snapshots;
  }
}
