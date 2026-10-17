package com.jiajunxie.service.impl;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.domain.loan.LoanRateSnapshotInput;
import com.jiajunxie.domain.loan.LoanRecastPreviewCommand;
import com.jiajunxie.domain.loan.LoanRecastPreviewResult;
import com.jiajunxie.domain.loan.LoanRepaymentCalculationRequest;
import com.jiajunxie.domain.loan.LoanRepaymentCalculationResult;
import com.jiajunxie.domain.loan.LoanRepaymentType;
import com.jiajunxie.domain.loan.LoanScheduleItem;
import com.jiajunxie.domain.loan.LoanSchedulePlanView;
import com.jiajunxie.service.LoanRecastPreviewService;
import com.jiajunxie.service.LoanRepaymentCalculator;
import com.jiajunxie.service.LoanScheduleQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanRecastPreviewServiceImpl implements LoanRecastPreviewService {
  private final LoanScheduleQueryService queryService;
  private final LoanRepaymentCalculator calculator;

  @Override
  public LoanRecastPreviewResult preview(LoanRecastPreviewCommand command) {
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

    LoanSchedulePlanView plan =
        queryService.getByPlanId(command.installmentPlanId(), LocalDate.now());
    List<LoanScheduleItem> future =
        plan.schedules().stream()
            .filter(item -> item.installmentNo() >= command.fromInstallmentNo())
            .toList();
    if (future.isEmpty() || future.get(0).installmentNo() != command.fromInstallmentNo()) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "重算起始期次不存在");
    }
    if (!future.stream().allMatch(item -> "PENDING".equals(item.storedStatusCode()))) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "Recast 只能作用于尚未开始结算的连续 PENDING 期次");
    }

    LoanScheduleItem first = future.get(0);
    LoanRepaymentType repaymentType = LoanRepaymentType.valueOf(first.repaymentTypeCode());
    if (repaymentType == LoanRepaymentType.MANUAL) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "MANUAL 计划不能自动 Recast，请继续使用人工调整");
    }

    BigDecimal currentRemainingBefore =
        command.fromInstallmentNo() == 1
            ? plan.originalPrincipal()
            : plan.schedules().get(command.fromInstallmentNo() - 2).remainingPrincipalAfter();
    if (command.remainingPrincipal().compareTo(currentRemainingBefore) > 0) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR, "Recast 剩余本金不能高于当前该期开始前的剩余本金");
    }

    int currentRemainingInstallments = future.size();
    BigDecimal currentNextPayment = first.effectiveTotalAmount();

    int proposedRemainingInstallments;
    LoanRepaymentCalculationResult proposed;
    if (command.strategy().name().equals("REDUCE_PAYMENT")) {
      proposedRemainingInstallments = currentRemainingInstallments;
      proposed =
          calculate(
              command.remainingPrincipal(),
              repaymentType,
              proposedRemainingInstallments,
              first.amountScale(),
              future);
    } else {
      proposedRemainingInstallments =
          findReducedTerm(
              command.remainingPrincipal(),
              repaymentType,
              first.amountScale(),
              future,
              currentNextPayment);
      proposed =
          calculate(
              command.remainingPrincipal(),
              repaymentType,
              proposedRemainingInstallments,
              first.amountScale(),
              future.subList(0, proposedRemainingInstallments));
    }

    return new LoanRecastPreviewResult(
        plan.installmentPlanId(),
        command.fromInstallmentNo(),
        command.strategy(),
        command.remainingPrincipal(),
        currentRemainingInstallments,
        proposedRemainingInstallments,
        currentNextPayment,
        proposed.installments().get(0).totalAmount(),
        false);
  }

  private int findReducedTerm(
      BigDecimal remainingPrincipal,
      LoanRepaymentType repaymentType,
      int amountScale,
      List<LoanScheduleItem> future,
      BigDecimal currentNextPayment) {
    for (int count = 1; count <= future.size(); count++) {
      LoanRepaymentCalculationResult candidate =
          calculate(
              remainingPrincipal,
              repaymentType,
              count,
              amountScale,
              future.subList(0, count));
      if (candidate.installments().get(0).totalAmount().compareTo(currentNextPayment) <= 0) {
        return count;
      }
    }
    return future.size();
  }

  private LoanRepaymentCalculationResult calculate(
      BigDecimal principal,
      LoanRepaymentType repaymentType,
      int installmentCount,
      int amountScale,
      List<LoanScheduleItem> sourceTimeline) {
    LoanScheduleItem first = sourceTimeline.get(0);
    return calculator.calculate(
        new LoanRepaymentCalculationRequest(
            principal,
            repaymentType,
            installmentCount,
            amountScale,
            12,
            first.annualRateSnapshot(),
            buildRelativeRateSnapshots(sourceTimeline, installmentCount),
            List.of()));
  }

  private List<LoanRateSnapshotInput> buildRelativeRateSnapshots(
      List<LoanScheduleItem> timeline, int count) {
    List<LoanRateSnapshotInput> snapshots = new ArrayList<>();
    BigDecimal previousRate = timeline.get(0).annualRateSnapshot();
    LocalDate previousEffective = timeline.get(0).rateEffectiveFrom();

    for (int i = 1; i < count; i++) {
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
