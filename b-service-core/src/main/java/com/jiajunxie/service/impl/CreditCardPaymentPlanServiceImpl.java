package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.CreditCardProfile;
import com.jiajunxie.entity.InstallmentPlan;
import com.jiajunxie.entity.PaymentSchedule;
import com.jiajunxie.mapper.CreditCardProfileMapper;
import com.jiajunxie.mapper.CreditRefundScheduleChangeMapper;
import com.jiajunxie.mapper.InstallmentPlanMapper;
import com.jiajunxie.mapper.PaymentScheduleMapper;
import com.jiajunxie.model.CreditRefundScheduleChange;
import com.jiajunxie.service.CreditCardDateRuleService;
import com.jiajunxie.service.CreditCardPaymentPlanService;
import com.jiajunxie.vo.CreditCardDatePreviewVo;
import com.jiajunxie.vo.CreditCardPaymentOptionVo;
import com.jiajunxie.vo.InstallmentPlanVo;
import com.jiajunxie.vo.PaymentScheduleVo;
import com.jiajunxie.vo.TransactionCreateVo;
import com.jiajunxie.vo.TransactionEntryVo;
import com.jiajunxie.vo.TransactionPaymentVo;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Generates future credit-card obligations without creating future accounting facts.
 *
 * <p>The purchase principal is already represented by TransactionEntry. Scheduled fees remain
 * plans only; this service never changes Account.totalAmount directly.
 */
@Service
@RequiredArgsConstructor
public class CreditCardPaymentPlanServiceImpl implements CreditCardPaymentPlanService {
  private final InstallmentPlanMapper planMapper;
  private final PaymentScheduleMapper scheduleMapper;
  private final CreditCardProfileMapper profileMapper;
  private final CreditCardDateRuleService dateRuleService;
  private final CreditRefundScheduleChangeMapper refundScheduleChangeMapper;

  @Override
  public void validatePayment(TransactionCreateVo transactionVo) {
    if (!"CREDIT_PURCHASE".equals(normalize(transactionVo.transactionType()))) return;

    TransactionPaymentVo payment = transactionVo.payment();
    // Transitional compatibility for F4 clients and historical transactions. The F5 frontend always
    // submits payment. Missing payment is kept temporarily until legacy data/API callers are migrated.
    if (payment == null) return;
    if (transactionVo.entries() == null || transactionVo.entries().size() != 1) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "信用卡消费必须且只能有一条账户分录");
    }

    TransactionEntryVo entry = transactionVo.entries().get(0);
    CreditCardProfile profile = requireEnabledProfile(entry.accountId());
    LocalDate purchaseDate = transactionVo.transactionTime().atZone(ZoneId.of(profile.getTimezone())).toLocalDate();

    String type = normalize(payment.paymentTypeCode());
    BigDecimal fee = payment.feeAmount() == null ? BigDecimal.ZERO : payment.feeAmount();
    if (fee.signum() < 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "分期手续费不能小于零");
    }

    switch (type) {
      case "NORMAL" -> {
        requireCount(payment.installmentCount(), 1, "NORMAL 只能有 1 期");
        if (fee.signum() != 0) {
          throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "NORMAL 不允许设置未来手续费");
        }
        if (hasText(payment.paymentRuleId())) {
          throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "NORMAL 不需要特殊支付规则");
        }
      }
      case "INSTALLMENT" -> {
        if (payment.installmentCount() == null || payment.installmentCount() < 2) {
          throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "分期付款期数必须大于等于 2");
        }
        if (hasText(payment.paymentRuleId())) {
          throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "INSTALLMENT 不使用特殊支付规则");
        }
      }
      case "DEFERRED", "SUMMER", "WINTER", "SPECIAL" -> {
        requireCount(payment.installmentCount(), 1, type + " 只能有 1 期");
        if (!hasText(payment.paymentRuleId())) {
          throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, type + " 必须选择支付规则");
        }
        CreditCardDatePreviewVo preview = dateRuleService.previewSaved(entry.accountId(), purchaseDate);
        requireSelectedOption(preview, payment.paymentRuleId(), type);
      }
      default -> throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "不支持的信用卡付款方式：" + type);
    }

    String remainderRule = normalizeRemainderRule(payment.remainderRuleCode());
    if ("MANUAL".equals(remainderRule)) {
      int count = payment.installmentCount() == null ? 1 : payment.installmentCount();
      int amountScale = requireAmountScale(profile);
      BigDecimal principal = money(entry.amount().abs(), amountScale);
      BigDecimal totalFee = money(fee, amountScale);
      validateManualAmounts(payment, count, principal, totalFee, amountScale);
    }
  }

  @Override
  @Transactional
  public void createPlan(String transactionId, TransactionCreateVo transactionVo) {
    if (!"CREDIT_PURCHASE".equals(normalize(transactionVo.transactionType()))
        || transactionVo.payment() == null) return;
    persistPlan(transactionId, transactionVo);
  }

  @Override
  @Transactional
  public void replacePlan(String transactionId, TransactionCreateVo transactionVo) {
    assertMutable(transactionId);
    deleteRows(transactionId);
    if ("CREDIT_PURCHASE".equals(normalize(transactionVo.transactionType()))
        && transactionVo.payment() != null) {
      persistPlan(transactionId, transactionVo);
    }
  }

  @Override
  @Transactional
  public void removePlan(String transactionId) {
    assertMutable(transactionId);
    deleteRows(transactionId);
  }

  @Override
  public boolean isLocked(String transactionId) {
    return schedules(transactionId).stream()
        .anyMatch(schedule -> !"PENDING".equals(normalize(schedule.getStatusCode())));
  }

  @Override
  public void assertMutable(String transactionId) {
    if (isLocked(transactionId)) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "该信用卡消费已进入已确认账单或已结算，不能直接修改财务字段或删除");
    }
  }

  @Override
  public InstallmentPlanVo getPlan(String transactionId) {
    InstallmentPlan plan = planMapper.selectOne(
        Wrappers.<InstallmentPlan>lambdaQuery()
            .eq(InstallmentPlan::getSourceTransactionId, transactionId));
    if (plan == null) return null;
    List<PaymentScheduleVo> scheduleVos = schedules(transactionId).stream().map(this::toVo).toList();
    return new InstallmentPlanVo(
        plan.getInstallmentPlanId(),
        plan.getPaymentTypeCode(),
        plan.getPaymentRuleId(),
        plan.getPrincipalAmount(),
        plan.getFeeAmount(),
        plan.getCurrency(),
        plan.getInstallmentCount(),
        plan.getFirstDueDate(),
        plan.getRemainderRuleCode(),
        plan.getStatusCode(),
        scheduleVos);
  }

  @Override
  @Transactional
  public void applyPendingPrincipalRefund(String transactionId, BigDecimal refundAmount) {
    applyPendingPrincipalRefund(transactionId, null, refundAmount);
  }

  @Override
  @Transactional
  public void applyPendingPrincipalRefund(
      String transactionId, String refundTransactionId, BigDecimal refundAmount) {
    if (refundAmount == null || refundAmount.signum() <= 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "退款金额必须大于 0");
    }
    InstallmentPlan plan = planMapper.selectOne(
        Wrappers.<InstallmentPlan>lambdaQuery()
            .eq(InstallmentPlan::getSourceTransactionId, transactionId));
    if (plan == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "信用卡消费付款计划不存在");
    }
    List<PaymentSchedule> rows = schedules(transactionId);
    if (rows.isEmpty() || rows.stream().anyMatch(item -> !"PENDING".equals(normalize(item.getStatusCode())))) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "信用卡消费已进入账单，退款请通过账单退款/冲减处理");
    }
    BigDecimal remaining = rows.stream()
        .map(PaymentSchedule::getScheduledPrincipalAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (refundAmount.compareTo(remaining) > 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "退款金额超过剩余可退款本金");
    }

    BigDecimal left = refundAmount;
    int changeOrder = 0;
    for (int index = rows.size() - 1; index >= 0 && left.signum() > 0; index--) {
      PaymentSchedule schedule = rows.get(index);
      BigDecimal principal = schedule.getScheduledPrincipalAmount();
      BigDecimal reduction = principal.min(left);
      BigDecimal nextPrincipal = principal.subtract(reduction);
      left = left.subtract(reduction);
      boolean deleteSchedule =
          nextPrincipal.signum() == 0
              && schedule.getScheduledFeeAmount().signum() == 0
              && schedule.getAdjustmentAmount().signum() == 0;

      if (refundTransactionId != null && !refundTransactionId.isBlank()) {
        CreditRefundScheduleChange change = new CreditRefundScheduleChange();
        change.setChangeId(UUID.randomUUID().toString());
        change.setRefundTransactionId(refundTransactionId);
        change.setPurchaseTransactionId(transactionId);
        change.setScheduleId(schedule.getScheduleId());
        change.setChangeOrder(changeOrder++);
        change.setPrincipalReduction(reduction);
        change.setScheduleDeleted(deleteSchedule);
        change.setInstallmentPlanId(schedule.getInstallmentPlanId());
        change.setLiabilityAccountId(schedule.getLiabilityAccountId());
        change.setInstallmentNo(schedule.getInstallmentNo());
        change.setInstallmentCount(schedule.getInstallmentCount());
        change.setPrincipalBefore(principal);
        change.setFeeBefore(schedule.getScheduledFeeAmount());
        change.setAdjustmentBefore(schedule.getAdjustmentAmount());
        change.setCurrency(schedule.getCurrency());
        change.setPaymentTypeCode(schedule.getPaymentTypeCode());
        change.setScheduledBillingDate(schedule.getScheduledBillingDate());
        change.setScheduledPaymentDate(schedule.getScheduledPaymentDate());
        change.setCalculatedPaymentDate(schedule.getCalculatedPaymentDate());
        change.setManualPaymentDate(schedule.getManualPaymentDate());
        change.setStatusCode(schedule.getStatusCode());
        change.setScheduleCreatedAt(schedule.getCreatedAt());
        change.setCreatedAt(Instant.now());
        refundScheduleChangeMapper.insert(change);
      }

      if (deleteSchedule) {
        scheduleMapper.deleteById(schedule.getScheduleId());
      } else {
        schedule.setScheduledPrincipalAmount(nextPrincipal);
        schedule.setUpdatedAt(Instant.now());
        scheduleMapper.updateById(schedule);
      }
    }
    plan.setPrincipalAmount(plan.getPrincipalAmount().subtract(refundAmount));
    plan.setUpdatedAt(Instant.now());
    planMapper.updateById(plan);
  }

  @Override
  @Transactional
  public void reversePendingPrincipalRefund(
      String transactionId, String refundTransactionId, BigDecimal refundAmount) {
    if (refundAmount == null || refundAmount.signum() <= 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "退款金额必须大于 0");
    }
    InstallmentPlan plan = planMapper.selectOne(
        Wrappers.<InstallmentPlan>lambdaQuery()
            .eq(InstallmentPlan::getSourceTransactionId, transactionId));
    if (plan == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "信用卡消费付款计划不存在");
    }
    List<CreditRefundScheduleChange> changes =
        refundScheduleChangeMapper.selectByRefundTransactionId(refundTransactionId);
    if (changes == null || changes.isEmpty()) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "该退款缺少可逆的付款计划审计记录；旧版退款请通过人工冲销处理");
    }
    BigDecimal recorded = changes.stream()
        .map(CreditRefundScheduleChange::getPrincipalReduction)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (recorded.compareTo(refundAmount) != 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "退款计划审计金额与退款交易不一致");
    }

    for (int index = changes.size() - 1; index >= 0; index--) {
      CreditRefundScheduleChange change = changes.get(index);
      PaymentSchedule current = scheduleMapper.selectById(change.getScheduleId());
      BigDecimal expectedCurrent = change.getPrincipalBefore().subtract(change.getPrincipalReduction());
      if (Boolean.TRUE.equals(change.getScheduleDeleted())) {
        if (current != null) {
          throw new BusinessException(
              CommonErrorCode.DATA_CONFLICT,
              "退款后的付款计划已被后续业务重建，不能直接撤销该退款");
        }
        PaymentSchedule restored = new PaymentSchedule();
        restored.setScheduleId(change.getScheduleId());
        restored.setInstallmentPlanId(change.getInstallmentPlanId());
        restored.setSourceTransactionId(transactionId);
        restored.setLiabilityAccountId(change.getLiabilityAccountId());
        restored.setInstallmentNo(change.getInstallmentNo());
        restored.setInstallmentCount(change.getInstallmentCount());
        restored.setScheduledPrincipalAmount(change.getPrincipalBefore());
        restored.setScheduledFeeAmount(change.getFeeBefore());
        restored.setAdjustmentAmount(change.getAdjustmentBefore());
        restored.setCurrency(change.getCurrency());
        restored.setPaymentTypeCode(change.getPaymentTypeCode());
        restored.setScheduledBillingDate(change.getScheduledBillingDate());
        restored.setScheduledPaymentDate(change.getScheduledPaymentDate());
        restored.setCalculatedPaymentDate(change.getCalculatedPaymentDate());
        restored.setManualPaymentDate(change.getManualPaymentDate());
        restored.setStatusCode(change.getStatusCode());
        restored.setCreatedAt(change.getScheduleCreatedAt());
        restored.setUpdatedAt(Instant.now());
        scheduleMapper.insert(restored);
      } else {
        if (current == null
            || !"PENDING".equals(normalize(current.getStatusCode()))
            || current.getScheduledPrincipalAmount().compareTo(expectedCurrent) != 0) {
          throw new BusinessException(
              CommonErrorCode.DATA_CONFLICT,
              "退款对应的付款计划已被后续业务修改，不能直接撤销；请使用新的冲销交易");
        }
        current.setScheduledPrincipalAmount(change.getPrincipalBefore());
        current.setUpdatedAt(Instant.now());
        scheduleMapper.updateById(current);
      }
    }

    plan.setPrincipalAmount(plan.getPrincipalAmount().add(refundAmount));
    plan.setUpdatedAt(Instant.now());
    planMapper.updateById(plan);
    refundScheduleChangeMapper.deleteByRefundTransactionId(refundTransactionId);
  }

  private void persistPlan(String transactionId, TransactionCreateVo transactionVo) {
    validatePayment(transactionVo);
    TransactionEntryVo entry = transactionVo.entries().get(0);
    TransactionPaymentVo payment = transactionVo.payment();
    CreditCardProfile profile = requireEnabledProfile(entry.accountId());
    ZoneId zoneId = ZoneId.of(profile.getTimezone());
    LocalDate purchaseDate = transactionVo.transactionTime().atZone(zoneId).toLocalDate();

    String type = normalize(payment.paymentTypeCode());
    int count = payment.installmentCount() == null ? 1 : payment.installmentCount();
    int amountScale = requireAmountScale(profile);
    BigDecimal principal = money(entry.amount().abs(), amountScale);
    BigDecimal fee =
        money(payment.feeAmount() == null ? BigDecimal.ZERO : payment.feeAmount(), amountScale);
    String remainderRule = normalizeRemainderRule(payment.remainderRuleCode());

    // Principal and future fee are split independently at the configured minimum monetary unit.
    // MANUAL means the user supplies the original per-installment plan; it is not a post-plan adjustment.
    List<BigDecimal> principals;
    List<BigDecimal> fees;
    if ("MANUAL".equals(remainderRule)) {
      principals = normalizeManualAmounts(payment.manualPrincipalAmounts(), count, amountScale);
      fees = normalizeManualAmounts(payment.manualFeeAmounts(), count, amountScale);
    } else {
      principals = split(principal, count, remainderRule, amountScale);
      fees = split(fee, count, remainderRule, amountScale);
    }

    // MyBatis inserts do not trigger JPA @PrePersist callbacks. Set audit timestamps explicitly.
    Instant now = Instant.now();

    InstallmentPlan plan = new InstallmentPlan();
    String planId = UUID.randomUUID().toString();
    plan.setInstallmentPlanId(planId);
    plan.setSourceTransactionId(transactionId);
    plan.setLiabilityAccountId(entry.accountId());
    plan.setPaymentTypeCode(type);
    plan.setPaymentRuleId(trimToNull(payment.paymentRuleId()));
    plan.setPrincipalAmount(principal);
    plan.setFeeAmount(fee);
    plan.setCurrency(normalize(entry.currency()));
    plan.setInstallmentCount(count);
    plan.setRemainderRuleCode(remainderRule);
    plan.setStatusCode("ACTIVE");
    plan.setCreatedAt(now);
    plan.setUpdatedAt(now);

    List<PaymentSchedule> generated = generateSchedules(
        planId, transactionId, entry.accountId(), type, payment.paymentRuleId(), purchaseDate,
        principals, fees, entry.currency(), profile, now);
    if (!generated.isEmpty()) plan.setFirstDueDate(generated.get(0).getCalculatedPaymentDate());

    planMapper.insert(plan);
    for (PaymentSchedule schedule : generated) scheduleMapper.insert(schedule);
  }

  private List<PaymentSchedule> generateSchedules(
      String planId,
      String transactionId,
      String accountId,
      String paymentType,
      String paymentRuleId,
      LocalDate purchaseDate,
      List<BigDecimal> principals,
      List<BigDecimal> fees,
      String currency,
      CreditCardProfile profile,
      Instant now) {
    List<PaymentSchedule> result = new ArrayList<>();
    int count = principals.size();

    if ("NORMAL".equals(paymentType) || "INSTALLMENT".equals(paymentType)) {
      LocalDate referenceDate = purchaseDate;
      for (int i = 0; i < count; i++) {
        CreditCardDatePreviewVo preview = dateRuleService.previewSaved(accountId, referenceDate);
        result.add(schedule(
            planId,
            transactionId,
            accountId,
            i + 1,
            count,
            principals.get(i),
            fees.get(i),
            currency,
            paymentType,
            preview.getPeriodEnd(),
            preview.getScheduledPaymentDate(),
            preview.getCalculatedPaymentDate(),
            now));
        referenceDate = preview.getPeriodEnd().plusDays(1);
      }
      return result;
    }

    CreditCardDatePreviewVo preview = dateRuleService.previewSaved(accountId, purchaseDate);
    CreditCardPaymentOptionVo option = requireSelectedOption(preview, paymentRuleId, paymentType);
    LocalDate billingDate = resolveBillingDateForSpecialPayment(profile, option.getScheduledPaymentDate());
    result.add(schedule(
        planId,
        transactionId,
        accountId,
        1,
        1,
        principals.get(0),
        fees.get(0),
        currency,
        paymentType,
        billingDate,
        option.getScheduledPaymentDate(),
        option.getCalculatedPaymentDate(),
        now));
    return result;
  }

  /** Maps a special target payment month back to the statement period that would be paid then. */
  private LocalDate resolveBillingDateForSpecialPayment(
      CreditCardProfile profile, LocalDate scheduledPaymentDate) {
    int paymentOffset = profile.getPaymentMonthOffset() == null ? 1 : profile.getPaymentMonthOffset();
    int endOffset = profile.getBillingEndMonthOffset() == null ? 0 : profile.getBillingEndMonthOffset();
    String invalidRule = normalize(profile.getInvalidDayRuleCode());
    YearMonth startMonth = YearMonth.from(scheduledPaymentDate).minusMonths(paymentOffset);
    return resolveDay(startMonth.plusMonths(endOffset), profile.getBillingEndDay(), invalidRule);
  }

  private LocalDate resolveDay(YearMonth month, int day, String invalidRule) {
    if (day <= month.lengthOfMonth()) return month.atDay(day);
    return switch (invalidRule) {
      case "NEXT_MONTH_FIRST_DAY" -> month.plusMonths(1).atDay(1);
      case "LAST_DAY" -> month.atEndOfMonth();
      default -> throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "不支持的无效日期规则：" + invalidRule);
    };
  }

  private PaymentSchedule schedule(
      String planId,
      String transactionId,
      String accountId,
      int installmentNo,
      int installmentCount,
      BigDecimal principal,
      BigDecimal fee,
      String currency,
      String paymentType,
      LocalDate billingDate,
      LocalDate scheduledPaymentDate,
      LocalDate calculatedPaymentDate,
      Instant now) {
    PaymentSchedule schedule = new PaymentSchedule();
    schedule.setScheduleId(UUID.randomUUID().toString());
    schedule.setInstallmentPlanId(planId);
    schedule.setSourceTransactionId(transactionId);
    schedule.setLiabilityAccountId(accountId);
    schedule.setInstallmentNo(installmentNo);
    schedule.setInstallmentCount(installmentCount);
    schedule.setScheduledPrincipalAmount(principal);
    schedule.setScheduledFeeAmount(fee);
    schedule.setAdjustmentAmount(BigDecimal.ZERO.setScale(principal.scale()));
    schedule.setCurrency(normalize(currency));
    schedule.setPaymentTypeCode(paymentType);
    schedule.setScheduledBillingDate(billingDate);
    schedule.setScheduledPaymentDate(scheduledPaymentDate);
    schedule.setCalculatedPaymentDate(calculatedPaymentDate);
    schedule.setStatusCode("PENDING");
    schedule.setCreatedAt(now);
    schedule.setUpdatedAt(now);
    return schedule;
  }

  private CreditCardPaymentOptionVo requireSelectedOption(
      CreditCardDatePreviewVo preview, String ruleId, String paymentType) {
    return preview.getPaymentOptions().stream()
        .filter(option -> ruleId.equals(option.getRuleId()))
        .filter(option -> paymentType.equals(normalize(option.getPaymentTypeCode())))
        .findFirst()
        .orElseThrow(() -> new BusinessException(
            CommonErrorCode.PARAMETER_ERROR,
            "所选特殊支付规则不适用于当前信用卡或交易日期"));
  }

  private List<BigDecimal> split(
      BigDecimal amount, int count, String remainderRule, int amountScale) {
    BigInteger units = money(amount, amountScale).movePointRight(amountScale).toBigIntegerExact();
    BigInteger[] qr = units.divideAndRemainder(BigInteger.valueOf(count));
    List<BigDecimal> result = new ArrayList<>(count);
    for (int i = 0; i < count; i++) result.add(new BigDecimal(qr[0], amountScale));
    if (qr[1].signum() != 0) {
      int target = "FIRST_INSTALLMENT".equals(remainderRule) ? 0 : count - 1;
      result.set(target, result.get(target).add(new BigDecimal(qr[1], amountScale)));
    }
    return result;
  }

  private List<PaymentSchedule> schedules(String transactionId) {
    return scheduleMapper.selectList(
        Wrappers.<PaymentSchedule>lambdaQuery()
            .eq(PaymentSchedule::getSourceTransactionId, transactionId)
            .orderByAsc(PaymentSchedule::getInstallmentNo));
  }

  private void deleteRows(String transactionId) {
    scheduleMapper.delete(
        Wrappers.<PaymentSchedule>lambdaQuery()
            .eq(PaymentSchedule::getSourceTransactionId, transactionId));
    planMapper.delete(
        Wrappers.<InstallmentPlan>lambdaQuery()
            .eq(InstallmentPlan::getSourceTransactionId, transactionId));
  }

  private CreditCardProfile requireEnabledProfile(String accountId) {
    CreditCardProfile profile = profileMapper.selectById(accountId);
    if (profile == null || !Boolean.TRUE.equals(profile.getEnabled())) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "信用卡账单配置不存在或已停用");
    }
    return profile;
  }

  private PaymentScheduleVo toVo(PaymentSchedule schedule) {
    return new PaymentScheduleVo(
        schedule.getScheduleId(),
        schedule.getInstallmentNo(),
        schedule.getInstallmentCount(),
        schedule.getScheduledPrincipalAmount(),
        schedule.getScheduledFeeAmount(),
        schedule.getAdjustmentAmount(),
        schedule.getCurrency(),
        schedule.getPaymentTypeCode(),
        schedule.getScheduledBillingDate(),
        schedule.getScheduledPaymentDate(),
        schedule.getCalculatedPaymentDate(),
        schedule.getManualPaymentDate(),
        schedule.getStatusCode());
  }

  private String normalizeRemainderRule(String value) {
    String rule = hasText(value) ? normalize(value) : "LAST_INSTALLMENT";
    if (!List.of("FIRST_INSTALLMENT", "LAST_INSTALLMENT", "MANUAL").contains(rule)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "不支持的尾差规则：" + rule);
    }
    return rule;
  }

  private void validateManualAmounts(
      TransactionPaymentVo payment,
      int count,
      BigDecimal principal,
      BigDecimal fee,
      int amountScale) {
    List<BigDecimal> principals = normalizeManualAmounts(payment.manualPrincipalAmounts(), count, amountScale);
    List<BigDecimal> fees = normalizeManualAmounts(payment.manualFeeAmounts(), count, amountScale);

    BigDecimal principalSum = principals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    if (principalSum.compareTo(principal) != 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "手工分期本金合计必须等于消费本金");
    }
    BigDecimal feeSum = fees.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    if (feeSum.compareTo(fee) != 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "手工分期手续费合计必须等于总手续费");
    }
  }

  private List<BigDecimal> normalizeManualAmounts(
      List<BigDecimal> values, int count, int amountScale) {
    if (values == null || values.size() != count) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "手工尾差必须填写与分期期数一致的每期金额");
    }
    List<BigDecimal> result = new ArrayList<>(count);
    for (BigDecimal value : values) {
      if (value == null || value.signum() < 0) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "手工分期金额不能为 null 或负数");
      }
      result.add(money(value, amountScale));
    }
    return result;
  }

  private void requireCount(Integer actual, int expected, String message) {
    int count = actual == null ? 1 : actual;
    if (count != expected) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, message);
  }

  private int requireAmountScale(CreditCardProfile profile) {
    Integer amountScale = profile.getAmountScale();
    if (amountScale == null || amountScale < 0 || amountScale > 4) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "信用卡金额小数位配置无效");
    }
    return amountScale;
  }

  private BigDecimal money(BigDecimal amount, int amountScale) {
    try {
      return amount.setScale(amountScale, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException exception) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR,
          "金额不符合信用卡配置的小数位要求（当前：" + amountScale + " 位）");
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String trimToNull(String value) {
    return hasText(value) ? value.trim() : null;
  }

  private static String normalize(String value) {
    return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
  }
}
