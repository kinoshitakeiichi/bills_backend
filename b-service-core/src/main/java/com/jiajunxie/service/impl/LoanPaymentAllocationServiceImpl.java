package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.domain.loan.LoanPaymentAllocationResult;
import com.jiajunxie.entity.InstallmentPlan;
import com.jiajunxie.entity.LoanPaymentAllocation;
import com.jiajunxie.entity.LoanPaymentDetail;
import com.jiajunxie.entity.LoanRepaymentScheduleDetail;
import com.jiajunxie.entity.PaymentSchedule;
import com.jiajunxie.mapper.InstallmentPlanMapper;
import com.jiajunxie.mapper.LoanPaymentAllocationMapper;
import com.jiajunxie.mapper.LoanPaymentDetailMapper;
import com.jiajunxie.mapper.LoanProfileMapper;
import com.jiajunxie.mapper.LoanRepaymentScheduleDetailMapper;
import com.jiajunxie.mapper.PaymentScheduleMapper;
import com.jiajunxie.service.LoanPaymentAllocationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoanPaymentAllocationServiceImpl implements LoanPaymentAllocationService {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private final LoanPaymentDetailMapper paymentDetailMapper;
  private final InstallmentPlanMapper planMapper;
  private final PaymentScheduleMapper scheduleMapper;
  private final LoanRepaymentScheduleDetailMapper scheduleDetailMapper;
  private final LoanPaymentAllocationMapper allocationMapper;
  private final LoanProfileMapper loanProfileMapper;

  @Override
  @Transactional
  public LoanPaymentAllocationResult allocate(String loanPaymentTransactionId) {
    return allocate(loanPaymentTransactionId, null);
  }

  @Override
  @Transactional
  public LoanPaymentAllocationResult allocate(String loanPaymentTransactionId, List<String> preferredScheduleIds) {
    if (loanPaymentTransactionId == null || loanPaymentTransactionId.isBlank()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "贷款还款交易不能为空");
    }

    LoanPaymentDetail payment = paymentDetailMapper.selectById(loanPaymentTransactionId);
    if (payment == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "贷款还款明细不存在");
    }

    // Serialize all allocation/reversal operations for the same loan account. Different payment
    // transactions can otherwise race while reading the same outstanding schedules.
    if (loanProfileMapper.selectByIdForUpdate(payment.getLoanAccountId()) == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "贷款配置不存在");
    }

    List<LoanPaymentAllocation> existing =
        allocationMapper.selectList(
            Wrappers.<LoanPaymentAllocation>lambdaQuery()
                .eq(
                    LoanPaymentAllocation::getLoanPaymentTransactionId,
                    loanPaymentTransactionId));
    Map<String, LoanPaymentAllocation> existingBySchedule =
        existing.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    LoanPaymentAllocation::getScheduleId, x -> x));

    BigDecimal alreadyPrincipal =
        existing.stream()
            .map(LoanPaymentAllocation::getAllocatedPrincipalAmount)
            .reduce(ZERO, BigDecimal::add);
    BigDecimal alreadyInterest =
        existing.stream()
            .map(LoanPaymentAllocation::getAllocatedInterestAmount)
            .reduce(ZERO, BigDecimal::add);
    BigDecimal alreadyFee =
        existing.stream()
            .map(LoanPaymentAllocation::getAllocatedFeeAmount)
            .reduce(ZERO, BigDecimal::add);

    BigDecimal remainingPrincipal =
        nonNegative(payment.getPrincipalAmount().subtract(alreadyPrincipal));
    BigDecimal remainingInterest =
        nonNegative(payment.getInterestAmount().subtract(alreadyInterest));
    BigDecimal remainingFee =
        nonNegative(payment.getFeeAmount().subtract(alreadyFee));

    if (remainingPrincipal.signum() == 0
        && remainingInterest.signum() == 0
        && remainingFee.signum() == 0) {
      return result(
          payment,
          alreadyPrincipal,
          alreadyInterest,
          alreadyFee,
          remainingPrincipal,
          remainingInterest,
          remainingFee,
          existingBySchedule.size());
    }

    List<InstallmentPlan> plans =
        planMapper.selectList(
            Wrappers.<InstallmentPlan>lambdaQuery()
                .eq(InstallmentPlan::getLiabilityAccountId, payment.getLoanAccountId())
                .eq(InstallmentPlan::getPaymentTypeCode, "LOAN")
                .eq(InstallmentPlan::getStatusCode, "ACTIVE")
                .orderByAsc(InstallmentPlan::getCreatedAt));

    if (plans.isEmpty()) {
      return result(
          payment,
          alreadyPrincipal,
          alreadyInterest,
          alreadyFee,
          remainingPrincipal,
          remainingInterest,
          remainingFee,
          existingBySchedule.size());
    }

    List<String> planIds = plans.stream().map(InstallmentPlan::getInstallmentPlanId).toList();
    List<PaymentSchedule> schedules =
        new ArrayList<>(
            scheduleMapper.selectList(
                Wrappers.<PaymentSchedule>lambdaQuery()
                    .in(PaymentSchedule::getInstallmentPlanId, planIds)
                    .in(PaymentSchedule::getStatusCode, "PENDING", "PARTIALLY_PAID")));

    if (schedules.isEmpty()) {
      return result(
          payment,
          alreadyPrincipal,
          alreadyInterest,
          alreadyFee,
          remainingPrincipal,
          remainingInterest,
          remainingFee,
          existingBySchedule.size());
    }

    List<String> scheduleIds = schedules.stream().map(PaymentSchedule::getScheduleId).toList();
    Map<String, LoanRepaymentScheduleDetail> detailBySchedule =
        scheduleDetailMapper.selectBatchIds(scheduleIds).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    LoanRepaymentScheduleDetail::getScheduleId, x -> x));

    Map<String, Totals> alreadyAllocated = allocatedTotals(scheduleIds);
    Set<String> preferred = preferredScheduleIds == null ? Set.of() : preferredScheduleIds.stream().filter(java.util.Objects::nonNull).map(String::trim).filter(x -> !x.isEmpty()).collect(java.util.stream.Collectors.toSet());
    if (!preferred.isEmpty()) {
      Set<String> availableIds = schedules.stream().map(PaymentSchedule::getScheduleId).collect(java.util.stream.Collectors.toSet());
      if (!availableIds.containsAll(preferred)) throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "指定贷款还款计划不存在、已结清或不属于当前贷款账户");
      schedules.removeIf(x -> !preferred.contains(x.getScheduleId()));
    }
    schedules.sort(Comparator.comparing(this::effectiveDate).thenComparing(PaymentSchedule::getInstallmentPlanId).thenComparing(PaymentSchedule::getInstallmentNo));

    BigDecimal allocatedPrincipal = alreadyPrincipal;
    BigDecimal allocatedInterest = alreadyInterest;
    BigDecimal allocatedFee = alreadyFee;
    java.util.Set<String> touchedSchedules = new java.util.HashSet<>(existingBySchedule.keySet());
    Instant now = Instant.now();

    for (PaymentSchedule schedule : schedules) {
      if (remainingPrincipal.signum() == 0
          && remainingInterest.signum() == 0
          && remainingFee.signum() == 0) {
        break;
      }

      LoanRepaymentScheduleDetail detail = detailBySchedule.get(schedule.getScheduleId());
      if (detail == null) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT,
            "贷款计划缺少 Loan Schedule Detail：" + schedule.getScheduleId());
      }

      Totals allocatedBefore = alreadyAllocated.getOrDefault(schedule.getScheduleId(), Totals.ZERO);
      BigDecimal duePrincipal =
          effective(detail.getManualPrincipalAmount(), schedule.getScheduledPrincipalAmount());
      BigDecimal dueInterest =
          effective(detail.getManualInterestAmount(), detail.getScheduledInterestAmount());
      BigDecimal dueFee =
          effective(detail.getManualFeeAmount(), schedule.getScheduledFeeAmount());

      BigDecimal outstandingPrincipal =
          nonNegative(duePrincipal.subtract(allocatedBefore.principal()));
      BigDecimal outstandingInterest =
          nonNegative(dueInterest.subtract(allocatedBefore.interest()));
      BigDecimal outstandingFee =
          nonNegative(dueFee.subtract(allocatedBefore.fee()));

      BigDecimal principalPart = min(remainingPrincipal, outstandingPrincipal);
      BigDecimal interestPart = min(remainingInterest, outstandingInterest);
      BigDecimal feePart = min(remainingFee, outstandingFee);

      if (principalPart.signum() == 0
          && interestPart.signum() == 0
          && feePart.signum() == 0) {
        continue;
      }

      LoanPaymentAllocation allocation = existingBySchedule.get(schedule.getScheduleId());
      if (allocation == null) {
        allocation = new LoanPaymentAllocation();
        allocation.setAllocationId(UUID.randomUUID().toString());
        allocation.setLoanPaymentTransactionId(payment.getTransactionId());
        allocation.setScheduleId(schedule.getScheduleId());
        allocation.setAllocatedPrincipalAmount(principalPart);
        allocation.setAllocatedInterestAmount(interestPart);
        allocation.setAllocatedFeeAmount(feePart);
        allocation.setCreatedAt(now);
        allocationMapper.insert(allocation);
        existingBySchedule.put(schedule.getScheduleId(), allocation);
      } else {
        allocation.setAllocatedPrincipalAmount(
            allocation.getAllocatedPrincipalAmount().add(principalPart));
        allocation.setAllocatedInterestAmount(
            allocation.getAllocatedInterestAmount().add(interestPart));
        allocation.setAllocatedFeeAmount(
            allocation.getAllocatedFeeAmount().add(feePart));
        allocationMapper.updateById(allocation);
      }

      remainingPrincipal = remainingPrincipal.subtract(principalPart);
      remainingInterest = remainingInterest.subtract(interestPart);
      remainingFee = remainingFee.subtract(feePart);
      allocatedPrincipal = allocatedPrincipal.add(principalPart);
      allocatedInterest = allocatedInterest.add(interestPart);
      allocatedFee = allocatedFee.add(feePart);
      touchedSchedules.add(schedule.getScheduleId());

      Totals after =
          allocatedBefore.add(new Totals(principalPart, interestPart, feePart));
      updateScheduleStatus(schedule, duePrincipal, dueInterest, dueFee, after, now);
      alreadyAllocated.put(schedule.getScheduleId(), after);
    }

    refreshPlanStatuses(plans, now);

    return result(
        payment,
        allocatedPrincipal,
        allocatedInterest,
        allocatedFee,
        remainingPrincipal,
        remainingInterest,
        remainingFee,
        touchedSchedules.size());
  }

  @Override
  @Transactional
  public void reverse(String loanPaymentTransactionId) {
    if (loanPaymentTransactionId == null || loanPaymentTransactionId.isBlank()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "贷款还款交易不能为空");
    }

    LoanPaymentDetail payment = paymentDetailMapper.selectById(loanPaymentTransactionId);
    if (payment != null && loanProfileMapper.selectByIdForUpdate(payment.getLoanAccountId()) == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "贷款配置不存在");
    }

    List<LoanPaymentAllocation> allocations =
        allocationMapper.selectList(
            Wrappers.<LoanPaymentAllocation>lambdaQuery()
                .eq(
                    LoanPaymentAllocation::getLoanPaymentTransactionId,
                    loanPaymentTransactionId));
    if (allocations.isEmpty()) {
      return;
    }

    List<String> scheduleIds =
        allocations.stream()
            .map(LoanPaymentAllocation::getScheduleId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    List<PaymentSchedule> schedules = scheduleMapper.selectBatchIds(scheduleIds);
    if (schedules.size() != scheduleIds.size()) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "贷款还款分配引用的还款计划不存在，不能安全撤销");
    }

    List<LoanRepaymentScheduleDetail> details = scheduleDetailMapper.selectBatchIds(scheduleIds);
    Map<String, LoanRepaymentScheduleDetail> detailBySchedule =
        details.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    LoanRepaymentScheduleDetail::getScheduleId, x -> x));
    if (detailBySchedule.size() != scheduleIds.size()) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "贷款还款分配引用的计划明细不完整，不能安全撤销");
    }

    java.util.Set<String> planIds =
        schedules.stream()
            .map(PaymentSchedule::getInstallmentPlanId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());

    allocationMapper.delete(
        Wrappers.<LoanPaymentAllocation>lambdaQuery()
            .eq(
                LoanPaymentAllocation::getLoanPaymentTransactionId,
                loanPaymentTransactionId));

    Map<String, Totals> remainingBySchedule = allocatedTotals(scheduleIds);
    Instant now = Instant.now();
    for (PaymentSchedule schedule : schedules) {
      LoanRepaymentScheduleDetail detail = detailBySchedule.get(schedule.getScheduleId());
      BigDecimal duePrincipal =
          effective(detail.getManualPrincipalAmount(), schedule.getScheduledPrincipalAmount());
      BigDecimal dueInterest =
          effective(detail.getManualInterestAmount(), detail.getScheduledInterestAmount());
      BigDecimal dueFee =
          effective(detail.getManualFeeAmount(), schedule.getScheduledFeeAmount());
      updateScheduleStatus(
          schedule,
          duePrincipal,
          dueInterest,
          dueFee,
          remainingBySchedule.getOrDefault(schedule.getScheduleId(), Totals.ZERO),
          now);
    }

    for (String planId : planIds) {
      InstallmentPlan plan = planMapper.selectById(planId);
      if (plan == null) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT, "贷款还款分配引用的分期计划不存在，不能安全撤销");
      }
      if ("CANCELLED".equals(plan.getStatusCode())) {
        continue;
      }
      List<PaymentSchedule> rows =
          scheduleMapper.selectList(
              Wrappers.<PaymentSchedule>lambdaQuery()
                  .eq(PaymentSchedule::getInstallmentPlanId, planId)
                  .ne(PaymentSchedule::getStatusCode, "CANCELLED"));
      boolean allPaid =
          !rows.isEmpty() && rows.stream().allMatch(x -> "PAID".equals(x.getStatusCode()));
      String nextStatus = allPaid ? "COMPLETED" : "ACTIVE";
      if (!nextStatus.equals(plan.getStatusCode())) {
        plan.setStatusCode(nextStatus);
        plan.setUpdatedAt(now);
        planMapper.updateById(plan);
      }
    }
  }

  private LoanPaymentAllocationResult result(
      LoanPaymentDetail payment,
      BigDecimal allocatedPrincipal,
      BigDecimal allocatedInterest,
      BigDecimal allocatedFee,
      BigDecimal unallocatedPrincipal,
      BigDecimal unallocatedInterest,
      BigDecimal unallocatedFee,
      int touchedScheduleCount) {
    return new LoanPaymentAllocationResult(
        payment.getTransactionId(),
        allocatedPrincipal,
        allocatedInterest,
        allocatedFee,
        unallocatedPrincipal,
        unallocatedInterest,
        unallocatedFee,
        touchedScheduleCount);
  }

  private Map<String, Totals> allocatedTotals(List<String> scheduleIds) {
    List<LoanPaymentAllocation> rows =
        allocationMapper.selectList(
            Wrappers.<LoanPaymentAllocation>lambdaQuery()
                .in(LoanPaymentAllocation::getScheduleId, scheduleIds));
    Map<String, Totals> result = new HashMap<>();
    for (LoanPaymentAllocation row : rows) {
      result.merge(
          row.getScheduleId(),
          new Totals(
              row.getAllocatedPrincipalAmount(),
              row.getAllocatedInterestAmount(),
              row.getAllocatedFeeAmount()),
          Totals::add);
    }
    return result;
  }

  private void updateScheduleStatus(
      PaymentSchedule schedule,
      BigDecimal duePrincipal,
      BigDecimal dueInterest,
      BigDecimal dueFee,
      Totals allocated,
      Instant now) {
    boolean principalPaid = allocated.principal().compareTo(duePrincipal) >= 0;
    boolean interestPaid = allocated.interest().compareTo(dueInterest) >= 0;
    boolean feePaid = allocated.fee().compareTo(dueFee) >= 0;
    String nextStatus;
    if (principalPaid && interestPaid && feePaid) {
      nextStatus = "PAID";
    } else if (allocated.total().signum() > 0) {
      nextStatus = "PARTIALLY_PAID";
    } else {
      nextStatus = "PENDING";
    }
    if (!nextStatus.equals(schedule.getStatusCode())) {
      schedule.setStatusCode(nextStatus);
      schedule.setUpdatedAt(now);
      scheduleMapper.updateById(schedule);
    }
  }

  private void refreshPlanStatuses(List<InstallmentPlan> plans, Instant now) {
    for (InstallmentPlan plan : plans) {
      List<PaymentSchedule> rows =
          scheduleMapper.selectList(
              Wrappers.<PaymentSchedule>lambdaQuery()
                  .eq(PaymentSchedule::getInstallmentPlanId, plan.getInstallmentPlanId())
                  .ne(PaymentSchedule::getStatusCode, "CANCELLED"));
      boolean allPaid = !rows.isEmpty() && rows.stream().allMatch(x -> "PAID".equals(x.getStatusCode()));
      if (allPaid && !"COMPLETED".equals(plan.getStatusCode())) {
        plan.setStatusCode("COMPLETED");
        plan.setUpdatedAt(now);
        planMapper.updateById(plan);
      }
    }
  }

  private LocalDate effectiveDate(PaymentSchedule schedule) {
    if (schedule.getManualPaymentDate() != null) return schedule.getManualPaymentDate();
    if (schedule.getCalculatedPaymentDate() != null) return schedule.getCalculatedPaymentDate();
    return schedule.getScheduledPaymentDate();
  }

  private BigDecimal effective(BigDecimal manual, BigDecimal system) {
    return manual == null ? system : manual;
  }

  private BigDecimal min(BigDecimal left, BigDecimal right) {
    return left.compareTo(right) <= 0 ? left : right;
  }

  private BigDecimal nonNegative(BigDecimal value) {
    return value.signum() < 0 ? ZERO : value;
  }

  private record Totals(BigDecimal principal, BigDecimal interest, BigDecimal fee) {
    private static final Totals ZERO =
        new Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    private Totals add(Totals other) {
      return new Totals(
          principal.add(other.principal),
          interest.add(other.interest),
          fee.add(other.fee));
    }

    private BigDecimal total() {
      return principal.add(interest).add(fee);
    }
  }
}
