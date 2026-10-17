package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.domain.loan.LoanRateSnapshotInput;
import com.jiajunxie.domain.loan.LoanRepaymentCalculationRequest;
import com.jiajunxie.domain.loan.LoanRepaymentCalculationResult;
import com.jiajunxie.domain.loan.LoanRepaymentInstallment;
import com.jiajunxie.domain.loan.LoanScheduleGenerationCommand;
import com.jiajunxie.domain.loan.LoanScheduleGenerationResult;
import com.jiajunxie.domain.loan.LoanScheduleGenerationPreviewItem;
import com.jiajunxie.domain.loan.LoanScheduleGenerationPreviewResult;
import com.jiajunxie.entity.InstallmentPlan;
import com.jiajunxie.entity.LoanProfile;
import com.jiajunxie.entity.LoanRateHistory;
import com.jiajunxie.entity.LoanRepaymentScheduleDetail;
import com.jiajunxie.entity.PaymentSchedule;
import com.jiajunxie.entity.LoanPaymentAllocation;
import com.jiajunxie.entity.ScheduleAdjustment;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.InstallmentPlanMapper;
import com.jiajunxie.mapper.LoanProfileMapper;
import com.jiajunxie.mapper.LoanRateHistoryMapper;
import com.jiajunxie.mapper.LoanRepaymentScheduleDetailMapper;
import com.jiajunxie.mapper.PaymentScheduleMapper;
import com.jiajunxie.mapper.LoanPaymentAllocationMapper;
import com.jiajunxie.mapper.ScheduleAdjustmentMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.BusinessCalendarService;
import com.jiajunxie.service.LoanRepaymentCalculator;
import com.jiajunxie.service.LoanScheduleGenerationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class LoanScheduleGenerationServiceImpl implements LoanScheduleGenerationService {
  private static final String PAYMENT_TYPE = "LOAN";

  private final LoanProfileMapper profileMapper;
  private final LoanRateHistoryMapper rateHistoryMapper;
  private final TransactionMapper transactionMapper;
  private final TransactionEntryMapper entryMapper;
  private final InstallmentPlanMapper planMapper;
  private final PaymentScheduleMapper scheduleMapper;
  private final LoanRepaymentScheduleDetailMapper detailMapper;
  private final LoanPaymentAllocationMapper loanPaymentAllocationMapper;
  private final ScheduleAdjustmentMapper scheduleAdjustmentMapper;
  private final LoanLifecycleDeletionGuard loanLifecycleDeletionGuard;
  private final LoanRepaymentCalculator calculator;
  private final BusinessCalendarService businessCalendarService;

  @Override
  @Transactional(readOnly = true)
  public LoanScheduleGenerationPreviewResult preview(LoanScheduleGenerationCommand command) {
    validateCommand(command);
    LoanProfile profile = requireProfile(command.loanAccountId());
    Transaction source = requireSourceTransaction(command.sourceTransactionId());
    validateFundingFact(profile, source, command.loanAccountId());
    assertNoExistingPlan(command.sourceTransactionId());

    TransactionEntry fundingEntry = requireFundingEntry(command.sourceTransactionId(), command.loanAccountId());
    BigDecimal principal = fundingEntry.getAmount().abs();
    if (principal.signum() <= 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "融资事实本金必须大于 0");
    }
    if (!normalize(profile.getCurrency()).equals(normalize(fundingEntry.getCurrency()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "融资事实币种与 Loan Profile 不一致");
    }

    List<LocalDate> scheduledDates = buildScheduledDates(profile, command);
    List<LoanRateSnapshotInput> rateSnapshots = buildRateSnapshots(profile, scheduledDates);
    LoanRepaymentCalculationResult calculation =
        calculator.calculate(
            new LoanRepaymentCalculationRequest(
                principal,
                command.repaymentType(),
                command.installmentCount(),
                command.amountScale(),
                12,
                profile.getAnnualRate(),
                rateSnapshots,
                command.manualInstallments()));

    List<LoanScheduleGenerationPreviewItem> rows = new ArrayList<>();
    for (LoanRepaymentInstallment installment : calculation.installments()) {
      LocalDate scheduledDate = scheduledDates.get(installment.installmentNo() - 1);
      rows.add(
          new LoanScheduleGenerationPreviewItem(
              installment.installmentNo(),
              adjustBusinessDay(scheduledDate, profile.getCalendarCode(), profile.getBusinessDayAdjustmentCode()),
              installment.principalAmount(),
              installment.interestAmount(),
              installment.feeAmount(),
              installment.totalAmount(),
              installment.remainingPrincipalAfter(),
              installment.annualRateSnapshot()));
    }
    return new LoanScheduleGenerationPreviewResult(
        source.getTransactionId(),
        command.loanAccountId(),
        normalize(profile.getCurrency()),
        calculation.originalPrincipal(),
        calculation.totalPrincipal(),
        calculation.totalInterest(),
        calculation.totalFee(),
        calculation.totalPayment(),
        calculation.calculationVersion(),
        rows);
  }

  @Override
  @Transactional
  public LoanScheduleGenerationResult generate(LoanScheduleGenerationCommand command) {
    validateCommand(command);
    LoanProfile profile = requireProfile(command.loanAccountId());
    Transaction source = requireSourceTransaction(command.sourceTransactionId());
    validateFundingFact(profile, source, command.loanAccountId());
    assertNoExistingPlan(command.sourceTransactionId());

    TransactionEntry fundingEntry = requireFundingEntry(command.sourceTransactionId(), command.loanAccountId());
    BigDecimal principal = fundingEntry.getAmount().abs();
    if (principal.signum() <= 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "融资事实本金必须大于 0");
    }
    if (!normalize(profile.getCurrency()).equals(normalize(fundingEntry.getCurrency()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "融资事实币种与 Loan Profile 不一致");
    }

    List<LocalDate> scheduledDates = buildScheduledDates(profile, command);
    List<LoanRateSnapshotInput> rateSnapshots =
        buildRateSnapshots(profile, scheduledDates);

    LoanRepaymentCalculationResult calculation =
        calculator.calculate(
            new LoanRepaymentCalculationRequest(
                principal,
                command.repaymentType(),
                command.installmentCount(),
                command.amountScale(),
                12,
                profile.getAnnualRate(),
                rateSnapshots,
                command.manualInstallments()));

    Instant now = Instant.now();
    String planId = UUID.randomUUID().toString();
    InstallmentPlan plan = new InstallmentPlan();
    plan.setInstallmentPlanId(planId);
    plan.setSourceTransactionId(source.getTransactionId());
    plan.setLiabilityAccountId(command.loanAccountId());
    plan.setPaymentTypeCode(PAYMENT_TYPE);
    plan.setPaymentRuleId(null);
    plan.setPrincipalAmount(calculation.totalPrincipal());
    plan.setFeeAmount(calculation.totalFee());
    plan.setCurrency(normalize(profile.getCurrency()));
    plan.setInstallmentCount(command.installmentCount());
    plan.setRemainderRuleCode(command.remainderRule().name());
    plan.setStatusCode("ACTIVE");
    plan.setCreatedAt(now);
    plan.setUpdatedAt(now);

    List<PaymentSchedule> schedules = new ArrayList<>();
    List<LoanRepaymentScheduleDetail> details = new ArrayList<>();
    for (LoanRepaymentInstallment installment : calculation.installments()) {
      if (installment.totalAmount().signum() <= 0) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "每一期计划还款总额必须大于 0");
      }
      LocalDate scheduledPaymentDate = scheduledDates.get(installment.installmentNo() - 1);
      LocalDate calculatedPaymentDate =
          adjustBusinessDay(
              scheduledPaymentDate,
              profile.getCalendarCode(),
              profile.getBusinessDayAdjustmentCode());

      PaymentSchedule schedule = new PaymentSchedule();
      String scheduleId = UUID.randomUUID().toString();
      schedule.setScheduleId(scheduleId);
      schedule.setInstallmentPlanId(planId);
      schedule.setSourceTransactionId(source.getTransactionId());
      schedule.setLiabilityAccountId(command.loanAccountId());
      schedule.setInstallmentNo(installment.installmentNo());
      schedule.setInstallmentCount(command.installmentCount());
      schedule.setScheduledPrincipalAmount(installment.principalAmount());
      schedule.setScheduledFeeAmount(installment.feeAmount());
      schedule.setAdjustmentAmount(BigDecimal.ZERO.setScale(command.amountScale()));
      schedule.setCurrency(normalize(profile.getCurrency()));
      schedule.setPaymentTypeCode(PAYMENT_TYPE);
      // Loans do not have a statement/billing period. The theoretical due date is retained here
      // to satisfy the generic schedule contract without inventing a second billing concept.
      schedule.setScheduledBillingDate(scheduledPaymentDate);
      schedule.setScheduledPaymentDate(scheduledPaymentDate);
      schedule.setCalculatedPaymentDate(calculatedPaymentDate);
      schedule.setManualPaymentDate(
          command.repaymentType() == com.jiajunxie.domain.loan.LoanRepaymentType.MANUAL
              ? scheduledPaymentDate
              : null);
      schedule.setStatusCode("PENDING");
      schedule.setCreatedAt(now);
      schedule.setUpdatedAt(now);
      schedules.add(schedule);

      LoanRepaymentScheduleDetail detail = new LoanRepaymentScheduleDetail();
      detail.setScheduleId(scheduleId);
      detail.setLoanAccountId(command.loanAccountId());
      detail.setRepaymentTypeCode(command.repaymentType().name());
      detail.setScheduledInterestAmount(installment.interestAmount());
      detail.setRemainingPrincipalAfter(installment.remainingPrincipalAfter());
      detail.setAnnualRateSnapshot(installment.annualRateSnapshot());
      detail.setRateEffectiveFrom(installment.rateEffectiveFrom());
      detail.setRemainderRuleCode(command.remainderRule().name());
      detail.setAdjustmentModeCode(command.adjustmentMode().name());
      detail.setCalculationVersion(calculation.calculationVersion());
      detail.setAmountScale(command.amountScale());
      detail.setGenerationNo(1);
      detail.setCreatedAt(now);
      details.add(detail);
    }

    if (!schedules.isEmpty()) {
      plan.setFirstDueDate(schedules.get(0).getCalculatedPaymentDate());
    }

    planMapper.insert(plan);
    for (PaymentSchedule schedule : schedules) {
      scheduleMapper.insert(schedule);
    }
    for (LoanRepaymentScheduleDetail detail : details) {
      detailMapper.insert(detail);
    }

    return new LoanScheduleGenerationResult(
        planId,
        source.getTransactionId(),
        command.loanAccountId(),
        command.installmentCount(),
        calculation.totalPrincipal(),
        calculation.totalInterest(),
        calculation.totalFee(),
        normalize(profile.getCurrency()),
        plan.getFirstDueDate(),
        calculation.calculationVersion());
  }

@Override
@Transactional
public int removePlan(String planId) {
  if (planId == null || planId.isBlank()) {
    throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "贷款还款计划不能为空");
  }

  InstallmentPlan plan = planMapper.selectById(planId);
  if (plan == null || !"LOAN".equals(normalize(plan.getPaymentTypeCode()))) {
    throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "贷款还款计划不存在");
  }

  // A loan with any actual repayment history is already historical. Plan deletion must not be
  // used to erase that history or work around the Loan lifecycle guard.
  loanLifecycleDeletionGuard.assertLoanAccountDeletable(plan.getLiabilityAccountId());

  List<PaymentSchedule> schedules =
      scheduleMapper.selectList(
          Wrappers.<PaymentSchedule>lambdaQuery()
              .eq(PaymentSchedule::getInstallmentPlanId, planId));
  List<String> scheduleIds =
      schedules.stream().map(PaymentSchedule::getScheduleId).toList();

  if (!scheduleIds.isEmpty()) {
    Long allocationCount =
        loanPaymentAllocationMapper.selectCount(
            Wrappers.<LoanPaymentAllocation>lambdaQuery()
                .in(LoanPaymentAllocation::getScheduleId, scheduleIds));
    if (allocationCount != null && allocationCount > 0) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "贷款还款计划已经存在实际还款分配，不能删除；请通过贷款还款撤销流程更正错误还款");
    }

    scheduleAdjustmentMapper.delete(
        Wrappers.<ScheduleAdjustment>lambdaQuery()
            .in(ScheduleAdjustment::getScheduleId, scheduleIds));
    detailMapper.delete(
        Wrappers.<LoanRepaymentScheduleDetail>lambdaQuery()
            .in(LoanRepaymentScheduleDetail::getScheduleId, scheduleIds));
    scheduleMapper.delete(
        Wrappers.<PaymentSchedule>lambdaQuery()
            .eq(PaymentSchedule::getInstallmentPlanId, planId));
  }

  return planMapper.deleteById(planId);
}

  private void validateCommand(LoanScheduleGenerationCommand command) {
    if (command == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "贷款还款计划生成参数不能为空");
    }
    requireText(command.loanAccountId(), "贷款账户不能为空");
    requireText(command.sourceTransactionId(), "融资事实交易不能为空");
    if (command.repaymentType() == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "还款方式不能为空");
    }
    if (command.installmentCount() < 1) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "还款期数必须大于 0");
    }
    if (command.amountScale() < 0 || command.amountScale() > 4) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "金额精度必须为 0-4");
    }
    if (command.firstScheduledPaymentDate() == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "首个计划还款日不能为空");
    }
    if (command.remainderRule() == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "余数规则不能为空");
    }
    if (command.adjustmentMode() == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "手工调整传播规则不能为空");
    }
  }

  private LoanProfile requireProfile(String accountId) {
    LoanProfile profile = profileMapper.selectById(accountId);
    if (profile == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "Loan Profile 不存在");
    }
    if (!"MONTHLY".equals(normalize(profile.getPaymentFrequencyCode()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "CP3 仅支持 MONTHLY 贷款计划");
    }
    if (profile.getPaymentDay() == null || profile.getPaymentDay() < 1 || profile.getPaymentDay() > 31) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "MONTHLY 贷款必须配置 1-31 的还款日");
    }
    return profile;
  }

  private Transaction requireSourceTransaction(String transactionId) {
    Transaction source = transactionMapper.selectById(transactionId);
    if (source == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "融资事实交易不存在");
    }
    if (!"LOAN_ENGINE".equals(normalize(source.getOriginCode()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "融资事实必须由 Loan Engine 创建");
    }
    return source;
  }

  private void validateFundingFact(LoanProfile profile, Transaction source, String accountId) {
    if (!accountId.equals(source.getOriginReferenceId())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "融资事实不属于指定贷款账户");
    }
    String fundingMode = normalize(profile.getFundingModeCode());
    String transactionType = normalize(source.getTransactionType());

    switch (fundingMode) {
      case "ACCOUNT_DISBURSEMENT" -> {
        if (!"LOAN_DISBURSEMENT".equals(transactionType)) {
          throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "账户放款模式只能基于 LOAN_DISBURSEMENT 生成计划");
        }
      }
      case "DIRECT_FINANCING", "PURCHASE_FINANCING" -> {
        if (!"LOAN_PURCHASE".equals(transactionType)) {
          throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "直接/逐笔融资模式只能基于 LOAN_PURCHASE 生成计划");
        }
      }
      default ->
          throw new BusinessException(
              CommonErrorCode.PARAMETER_ERROR, "不支持的贷款资金形成方式：" + fundingMode);
    }
  }

  private TransactionEntry requireFundingEntry(String transactionId, String accountId) {
    List<TransactionEntry> entries =
        entryMapper.selectList(
            Wrappers.<TransactionEntry>lambdaQuery()
                .eq(TransactionEntry::getTransactionId, transactionId)
                .eq(TransactionEntry::getAccountId, accountId));
    if (entries.size() != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "融资事实必须且只能有一条贷款账户分录");
    }
    return entries.get(0);
  }

  private void assertNoExistingPlan(String sourceTransactionId) {
    Long count =
        planMapper.selectCount(
            Wrappers.<InstallmentPlan>lambdaQuery()
                .eq(InstallmentPlan::getSourceTransactionId, sourceTransactionId));
    if (count != null && count > 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "该融资事实已经生成还款计划");
    }
  }

  private List<LocalDate> buildScheduledDates(
      LoanProfile profile, LoanScheduleGenerationCommand command) {
    if (command.repaymentType() == com.jiajunxie.domain.loan.LoanRepaymentType.MANUAL
        && command.manualInstallments() != null
        && command.manualInstallments().size() == command.installmentCount()
        && command.manualInstallments().stream().allMatch(row -> row.paymentDate() != null)) {
      return command.manualInstallments().stream()
          .sorted(Comparator.comparingInt(com.jiajunxie.domain.loan.LoanManualInstallmentInput::installmentNo))
          .map(com.jiajunxie.domain.loan.LoanManualInstallmentInput::paymentDate)
          .toList();
    }
    List<LocalDate> dates = new ArrayList<>(command.installmentCount());
    LocalDate first = command.firstScheduledPaymentDate();
    dates.add(first);

    YearMonth firstMonth = YearMonth.from(first);
    for (int i = 1; i < command.installmentCount(); i++) {
      YearMonth month = firstMonth.plusMonths(i);
      dates.add(month.atDay(Math.min(profile.getPaymentDay(), month.lengthOfMonth())));
    }
    return dates;
  }

  private List<LoanRateSnapshotInput> buildRateSnapshots(
      LoanProfile profile, List<LocalDate> scheduledDates) {
    if (!"VARIABLE".equals(normalize(profile.getRateTypeCode()))) {
      return List.of();
    }

    List<LoanRateHistory> history =
        rateHistoryMapper.selectList(
            Wrappers.<LoanRateHistory>lambdaQuery()
                .eq(LoanRateHistory::getAccountId, profile.getAccountId())
                .orderByAsc(LoanRateHistory::getEffectiveDate));
    history =
        history.stream()
            .sorted(Comparator.comparing(LoanRateHistory::getEffectiveDate))
            .toList();

    List<LoanRateSnapshotInput> snapshots = new ArrayList<>();
    BigDecimal previousRate = null;
    LocalDate previousEffectiveFrom = null;
    for (int i = 0; i < scheduledDates.size(); i++) {
      LocalDate dueDate = scheduledDates.get(i);
      LoanRateHistory applicable = null;
      for (LoanRateHistory row : history) {
        if (row.getEffectiveDate().isAfter(dueDate)) break;
        applicable = row;
      }

      BigDecimal rate = applicable == null ? profile.getAnnualRate() : applicable.getAnnualRate();
      LocalDate effectiveFrom = applicable == null ? null : applicable.getEffectiveDate();

      if (previousRate == null
          || previousRate.compareTo(rate) != 0
          || !java.util.Objects.equals(previousEffectiveFrom, effectiveFrom)) {
        snapshots.add(new LoanRateSnapshotInput(i + 1, rate, effectiveFrom));
        previousRate = rate;
        previousEffectiveFrom = effectiveFrom;
      }
    }
    return snapshots;
  }

  private LocalDate adjustBusinessDay(LocalDate date, String calendarCode, String ruleCode) {
    String rule = normalize(ruleCode);
    if (rule == null || "NONE".equals(rule)) return date;
    if (businessCalendarService.isBusinessDay(calendarCode, date)) return date;

    return switch (rule) {
      case "FOLLOWING" -> seekBusinessDay(date, calendarCode, 1);
      case "PRECEDING" -> seekBusinessDay(date, calendarCode, -1);
      case "MODIFIED_FOLLOWING" -> {
        LocalDate following = seekBusinessDay(date, calendarCode, 1);
        yield following.getMonth() == date.getMonth()
            ? following
            : seekBusinessDay(date, calendarCode, -1);
      }
      default ->
          throw new BusinessException(
              CommonErrorCode.PARAMETER_ERROR, "不支持的营业日调整规则：" + rule);
    };
  }

  private LocalDate seekBusinessDay(LocalDate date, String calendarCode, int direction) {
    LocalDate cursor = date;
    for (int i = 0; i < 370; i++) {
      cursor = cursor.plusDays(direction);
      if (businessCalendarService.isBusinessDay(calendarCode, cursor)) return cursor;
    }
    throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "无法在合理范围内找到营业日");
  }

  private void requireText(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, message);
    }
  }

  private String normalize(String value) {
    return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
  }
}
