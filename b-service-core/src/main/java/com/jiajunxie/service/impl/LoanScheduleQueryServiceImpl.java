package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.domain.loan.LoanScheduleItem;
import com.jiajunxie.domain.loan.LoanSchedulePlanView;
import com.jiajunxie.entity.InstallmentPlan;
import com.jiajunxie.entity.LoanRepaymentScheduleDetail;
import com.jiajunxie.entity.LoanPaymentAllocation;
import com.jiajunxie.entity.PaymentSchedule;
import com.jiajunxie.mapper.InstallmentPlanMapper;
import com.jiajunxie.mapper.LoanRepaymentScheduleDetailMapper;
import com.jiajunxie.mapper.LoanPaymentAllocationMapper;
import com.jiajunxie.mapper.PaymentScheduleMapper;
import com.jiajunxie.service.LoanScheduleQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanScheduleQueryServiceImpl implements LoanScheduleQueryService {
  private final InstallmentPlanMapper planMapper;
  private final PaymentScheduleMapper scheduleMapper;
  private final LoanRepaymentScheduleDetailMapper detailMapper;
  private final LoanPaymentAllocationMapper allocationMapper;

  @Override
  public List<LoanSchedulePlanView> listByLoanAccount(String loanAccountId, LocalDate asOfDate) {
    if (loanAccountId == null || loanAccountId.isBlank()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "贷款账户不能为空");
    }
    LocalDate asOf = asOfDate == null ? LocalDate.now() : asOfDate;
    return planMapper
        .selectList(
            Wrappers.<InstallmentPlan>lambdaQuery()
                .eq(InstallmentPlan::getLiabilityAccountId, loanAccountId)
                .eq(InstallmentPlan::getPaymentTypeCode, "LOAN")
                .orderByAsc(InstallmentPlan::getCreatedAt))
        .stream()
        .map(plan -> buildView(plan, asOf))
        .toList();
  }

  @Override
  public LoanSchedulePlanView getByPlanId(String installmentPlanId, LocalDate asOfDate) {
    if (installmentPlanId == null || installmentPlanId.isBlank()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "还款计划 ID 不能为空");
    }
    InstallmentPlan plan = planMapper.selectById(installmentPlanId);
    if (plan == null || !"LOAN".equals(plan.getPaymentTypeCode())) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "贷款还款计划不存在");
    }
    return buildView(plan, asOfDate == null ? LocalDate.now() : asOfDate);
  }

  private LoanSchedulePlanView buildView(InstallmentPlan plan, LocalDate asOfDate) {
    List<PaymentSchedule> schedules =
        scheduleMapper.selectList(
            Wrappers.<PaymentSchedule>lambdaQuery()
                .eq(PaymentSchedule::getInstallmentPlanId, plan.getInstallmentPlanId())
                .ne(PaymentSchedule::getStatusCode, "CANCELLED")
                .orderByAsc(PaymentSchedule::getInstallmentNo));

    if (schedules.isEmpty()) {
      return new LoanSchedulePlanView(
          plan.getInstallmentPlanId(),
          plan.getSourceTransactionId(),
          plan.getLiabilityAccountId(),
          plan.getCurrency(),
          plan.getPrincipalAmount(),
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          List.of());
    }

    List<String> scheduleIds = schedules.stream().map(PaymentSchedule::getScheduleId).toList();
    Map<String, LoanRepaymentScheduleDetail> details =
        detailMapper
            .selectBatchIds(scheduleIds)
            .stream()
            .collect(
                Collectors.toMap(
                    LoanRepaymentScheduleDetail::getScheduleId, Function.identity()));

    Map<String, AllocationTotals> allocations =
        allocationMapper
            .selectList(
                Wrappers.<LoanPaymentAllocation>lambdaQuery()
                    .in(LoanPaymentAllocation::getScheduleId, scheduleIds))
            .stream()
            .collect(
                Collectors.toMap(
                    LoanPaymentAllocation::getScheduleId,
                    row ->
                        new AllocationTotals(
                            row.getAllocatedPrincipalAmount(),
                            row.getAllocatedInterestAmount(),
                            row.getAllocatedFeeAmount()),
                    AllocationTotals::add));

    Map<Integer, BigDecimal> principalAdjustmentByGeneration = new java.util.HashMap<>();
    BigDecimal totalPrincipal = BigDecimal.ZERO;
    BigDecimal totalInterest = BigDecimal.ZERO;
    BigDecimal totalFee = BigDecimal.ZERO;
    List<LoanScheduleItem> items = new ArrayList<>();

    for (PaymentSchedule schedule : schedules) {
      LoanRepaymentScheduleDetail detail = details.get(schedule.getScheduleId());
      if (detail == null) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT,
            "贷款计划缺少 Loan Schedule Detail：" + schedule.getScheduleId());
      }

      BigDecimal principal =
          effective(detail.getManualPrincipalAmount(), schedule.getScheduledPrincipalAmount());
      BigDecimal interest =
          effective(detail.getManualInterestAmount(), detail.getScheduledInterestAmount());
      BigDecimal fee = effective(detail.getManualFeeAmount(), schedule.getScheduledFeeAmount());

      BigDecimal principalDelta =
          principal.subtract(schedule.getScheduledPrincipalAmount());
      BigDecimal generationDelta =
          principalAdjustmentByGeneration.merge(
              detail.getGenerationNo(), principalDelta, BigDecimal::add);
      BigDecimal effectiveRemainingPrincipal =
          nonNegative(detail.getRemainingPrincipalAfter().subtract(generationDelta));

      totalPrincipal = totalPrincipal.add(principal);
      totalInterest = totalInterest.add(interest);
      totalFee = totalFee.add(fee);

      AllocationTotals allocated =
          allocations.getOrDefault(schedule.getScheduleId(), AllocationTotals.ZERO);
      BigDecimal outstandingPrincipal = nonNegative(principal.subtract(allocated.principal()));
      BigDecimal outstandingInterest = nonNegative(interest.subtract(allocated.interest()));
      BigDecimal outstandingFee = nonNegative(fee.subtract(allocated.fee()));

      LocalDate effectiveDate =
          schedule.getManualPaymentDate() != null
              ? schedule.getManualPaymentDate()
              : schedule.getCalculatedPaymentDate();
      String displayStatus = displayStatus(schedule.getStatusCode(), effectiveDate, asOfDate);

      items.add(
          new LoanScheduleItem(
              schedule.getScheduleId(),
              schedule.getInstallmentPlanId(),
              schedule.getSourceTransactionId(),
              schedule.getLiabilityAccountId(),
              schedule.getInstallmentNo(),
              schedule.getInstallmentCount(),
              detail.getRepaymentTypeCode(),
              schedule.getScheduledPrincipalAmount(),
              detail.getScheduledInterestAmount(),
              schedule.getScheduledFeeAmount(),
              principal,
              interest,
              fee,
              principal.add(interest).add(fee),
              allocated.principal(),
              allocated.interest(),
              allocated.fee(),
              outstandingPrincipal,
              outstandingInterest,
              outstandingFee,
              outstandingPrincipal.add(outstandingInterest).add(outstandingFee),
              effectiveRemainingPrincipal,
              detail.getAnnualRateSnapshot(),
              detail.getRateEffectiveFrom(),
              schedule.getScheduledPaymentDate(),
              schedule.getCalculatedPaymentDate(),
              schedule.getManualPaymentDate(),
              effectiveDate,
              schedule.getStatusCode(),
              displayStatus,
              detail.getAdjustmentModeCode(),
              detail.getCalculationVersion(),
              detail.getAmountScale(),
              detail.getGenerationNo()));
    }

    return new LoanSchedulePlanView(
        plan.getInstallmentPlanId(),
        plan.getSourceTransactionId(),
        plan.getLiabilityAccountId(),
        plan.getCurrency(),
        plan.getPrincipalAmount(),
        totalPrincipal,
        totalInterest,
        totalFee,
        List.copyOf(items));
  }

  private BigDecimal effective(BigDecimal manual, BigDecimal system) {
    return manual == null ? system : manual;
  }

  private BigDecimal nonNegative(BigDecimal value) {
    return value.signum() < 0 ? BigDecimal.ZERO : value;
  }

  private record AllocationTotals(
      BigDecimal principal, BigDecimal interest, BigDecimal fee) {
    private static final AllocationTotals ZERO =
        new AllocationTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    private AllocationTotals add(AllocationTotals other) {
      return new AllocationTotals(
          principal.add(other.principal),
          interest.add(other.interest),
          fee.add(other.fee));
    }
  }

  private String displayStatus(String storedStatus, LocalDate effectiveDate, LocalDate asOfDate) {
    if (("PENDING".equals(storedStatus) || "PARTIALLY_PAID".equals(storedStatus))
        && effectiveDate != null
        && effectiveDate.isBefore(asOfDate)) {
      return "OVERDUE";
    }
    return storedStatus;
  }
}
