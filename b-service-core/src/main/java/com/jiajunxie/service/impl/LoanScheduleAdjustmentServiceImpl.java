package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.domain.loan.LoanScheduleAmountAdjustmentCommand;
import com.jiajunxie.domain.loan.LoanScheduleAmountField;
import com.jiajunxie.domain.loan.LoanScheduleDateAdjustmentCommand;
import com.jiajunxie.entity.LoanRepaymentScheduleDetail;
import com.jiajunxie.entity.PaymentSchedule;
import com.jiajunxie.entity.ScheduleAdjustment;
import com.jiajunxie.mapper.LoanRepaymentScheduleDetailMapper;
import com.jiajunxie.mapper.PaymentScheduleMapper;
import com.jiajunxie.mapper.ScheduleAdjustmentMapper;
import com.jiajunxie.service.LoanScheduleAdjustmentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoanScheduleAdjustmentServiceImpl implements LoanScheduleAdjustmentService {
  private final PaymentScheduleMapper scheduleMapper;
  private final LoanRepaymentScheduleDetailMapper detailMapper;
  private final ScheduleAdjustmentMapper adjustmentMapper;

  @Override
  @Transactional
  public void adjustAmount(LoanScheduleAmountAdjustmentCommand command) {
    if (command == null || command.scheduleId() == null || command.scheduleId().isBlank()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "计划期次不能为空");
    }
    if (command.field() == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "调整字段不能为空");
    }
    if (command.newAmount() == null || command.newAmount().signum() < 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "调整金额不能为负数");
    }
    requireReason(command.reason());

    PaymentSchedule schedule = requireMutableSchedule(command.scheduleId());
    LoanRepaymentScheduleDetail detail = requireDetail(command.scheduleId());

    BigDecimal before = effectiveAmount(schedule, detail, command.field());
    BigDecimal after = command.newAmount();
    if (before.compareTo(after) == 0) return;

    if (command.field() == LoanScheduleAmountField.PRINCIPAL
        && "ADJUST_LAST".equals(detail.getAdjustmentModeCode())) {
      applyPrincipalWithLastAdjustment(schedule, detail, before, after, command.reason());
    } else {
      setManualAmount(schedule, detail, command.field(), after);
      detailMapper.updateById(detail);
      if (command.field() == LoanScheduleAmountField.PRINCIPAL) {
        validatePlanPrincipalDoesNotExceedOriginal(schedule.getInstallmentPlanId());
      }
      writeAmountAudit(schedule.getScheduleId(), auditType(command.field()), before, after, command.reason());
    }
  }

  @Override
  @Transactional
  public void adjustDate(LoanScheduleDateAdjustmentCommand command) {
    if (command == null || command.scheduleId() == null || command.scheduleId().isBlank()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "计划期次不能为空");
    }
    requireReason(command.reason());

    PaymentSchedule schedule = requireMutableSchedule(command.scheduleId());
    LocalDate before =
        schedule.getManualPaymentDate() != null
            ? schedule.getManualPaymentDate()
            : schedule.getCalculatedPaymentDate();
    LocalDate after =
        command.manualPaymentDate() != null
            ? command.manualPaymentDate()
            : schedule.getCalculatedPaymentDate();
    if (before.equals(after) && schedule.getManualPaymentDate() == null == (command.manualPaymentDate() == null)) {
      return;
    }

    schedule.setManualPaymentDate(command.manualPaymentDate());
    schedule.setUpdatedAt(Instant.now());
    scheduleMapper.updateById(schedule);
    writeDateAudit(schedule.getScheduleId(), before, after, command.reason());
  }

  private void applyPrincipalWithLastAdjustment(
      PaymentSchedule target,
      LoanRepaymentScheduleDetail targetDetail,
      BigDecimal before,
      BigDecimal after,
      String reason) {
    List<PaymentSchedule> schedules =
        scheduleMapper.selectList(
            Wrappers.<PaymentSchedule>lambdaQuery()
                .eq(PaymentSchedule::getInstallmentPlanId, target.getInstallmentPlanId())
                .orderByAsc(PaymentSchedule::getInstallmentNo));
    PaymentSchedule lastMutable =
        schedules.stream()
            .filter(s -> "PENDING".equals(s.getStatusCode()))
            .reduce((first, second) -> second)
            .orElseThrow(
                () -> new BusinessException(CommonErrorCode.DATA_CONFLICT, "没有可用于吸收尾差的待还期次"));

    if (lastMutable.getScheduleId().equals(target.getScheduleId())) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR,
          "ADJUST_LAST 模式下最后一期本金是尾差吸收结果；如需直接修改最后一期，请使用 KEEP_REMAINING");
    }

    LoanRepaymentScheduleDetail lastDetail = requireDetail(lastMutable.getScheduleId());
    BigDecimal lastBefore = effectiveAmount(lastMutable, lastDetail, LoanScheduleAmountField.PRINCIPAL);
    BigDecimal delta = after.subtract(before);
    BigDecimal lastAfter = lastBefore.subtract(delta);
    if (lastAfter.signum() < 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "本次本金调整超过最后一期可吸收的余数");
    }

    setManualAmount(target, targetDetail, LoanScheduleAmountField.PRINCIPAL, after);
    setManualAmount(lastMutable, lastDetail, LoanScheduleAmountField.PRINCIPAL, lastAfter);
    detailMapper.updateById(targetDetail);
    detailMapper.updateById(lastDetail);

    writeAmountAudit(target.getScheduleId(), "LOAN_PRINCIPAL", before, after, reason);
    writeAmountAudit(
        lastMutable.getScheduleId(),
        "LOAN_PRINCIPAL_AUTO_REMAINDER",
        lastBefore,
        lastAfter,
        "自动尾差调整：" + reason);
  }

  private void validatePlanPrincipalDoesNotExceedOriginal(String planId) {
    // ADJUST_LAST normally reconciles exactly; this guard protects the special case where
    // the adjusted target itself is the final mutable installment.
    List<PaymentSchedule> schedules =
        scheduleMapper.selectList(
            Wrappers.<PaymentSchedule>lambdaQuery()
                .eq(PaymentSchedule::getInstallmentPlanId, planId)
                .ne(PaymentSchedule::getStatusCode, "CANCELLED")
                .orderByAsc(PaymentSchedule::getInstallmentNo));
    BigDecimal effectiveTotal = BigDecimal.ZERO;
    BigDecimal systemTotal = BigDecimal.ZERO;
    for (PaymentSchedule schedule : schedules) {
      LoanRepaymentScheduleDetail detail = requireDetail(schedule.getScheduleId());
      systemTotal = systemTotal.add(schedule.getScheduledPrincipalAmount());
      effectiveTotal =
          effectiveTotal.add(
              effectiveAmount(schedule, detail, LoanScheduleAmountField.PRINCIPAL));
    }
    if (effectiveTotal.compareTo(systemTotal) > 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "调整后计划本金不能超过原始计划本金");
    }
  }

  private PaymentSchedule requireMutableSchedule(String scheduleId) {
    PaymentSchedule schedule = scheduleMapper.selectById(scheduleId);
    if (schedule == null || !"LOAN".equals(schedule.getPaymentTypeCode())) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "贷款计划期次不存在");
    }
    if (!"PENDING".equals(schedule.getStatusCode())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "只有 PENDING 的贷款计划期次允许人工调整");
    }
    return schedule;
  }

  private LoanRepaymentScheduleDetail requireDetail(String scheduleId) {
    LoanRepaymentScheduleDetail detail = detailMapper.selectById(scheduleId);
    if (detail == null) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款计划缺少 Loan Schedule Detail");
    }
    return detail;
  }

  private BigDecimal effectiveAmount(
      PaymentSchedule schedule,
      LoanRepaymentScheduleDetail detail,
      LoanScheduleAmountField field) {
    return switch (field) {
      case PRINCIPAL ->
          detail.getManualPrincipalAmount() == null
              ? schedule.getScheduledPrincipalAmount()
              : detail.getManualPrincipalAmount();
      case INTEREST ->
          detail.getManualInterestAmount() == null
              ? detail.getScheduledInterestAmount()
              : detail.getManualInterestAmount();
      case FEE ->
          detail.getManualFeeAmount() == null
              ? schedule.getScheduledFeeAmount()
              : detail.getManualFeeAmount();
    };
  }

  private void setManualAmount(
      PaymentSchedule schedule,
      LoanRepaymentScheduleDetail detail,
      LoanScheduleAmountField field,
      BigDecimal amount) {
    switch (field) {
      case PRINCIPAL ->
          detail.setManualPrincipalAmount(
              amount.compareTo(schedule.getScheduledPrincipalAmount()) == 0 ? null : amount);
      case INTEREST ->
          detail.setManualInterestAmount(
              amount.compareTo(detail.getScheduledInterestAmount()) == 0 ? null : amount);
      case FEE ->
          detail.setManualFeeAmount(
              amount.compareTo(schedule.getScheduledFeeAmount()) == 0 ? null : amount);
    }
  }

  private String auditType(LoanScheduleAmountField field) {
    return switch (field) {
      case PRINCIPAL -> "LOAN_PRINCIPAL";
      case INTEREST -> "LOAN_INTEREST";
      case FEE -> "LOAN_FEE";
    };
  }

  private void writeAmountAudit(
      String scheduleId, String type, BigDecimal before, BigDecimal after, String reason) {
    ScheduleAdjustment audit = new ScheduleAdjustment();
    audit.setAdjustmentId(UUID.randomUUID().toString());
    audit.setScheduleId(scheduleId);
    audit.setAdjustmentTypeCode(type);
    audit.setBeforeAmount(before);
    audit.setAfterAmount(after);
    audit.setReason(reason.trim());
    audit.setCreatedAt(Instant.now());
    adjustmentMapper.insert(audit);
  }

  private void writeDateAudit(
      String scheduleId, LocalDate before, LocalDate after, String reason) {
    ScheduleAdjustment audit = new ScheduleAdjustment();
    audit.setAdjustmentId(UUID.randomUUID().toString());
    audit.setScheduleId(scheduleId);
    audit.setAdjustmentTypeCode("LOAN_PAYMENT_DATE");
    audit.setBeforeDate(before);
    audit.setAfterDate(after);
    audit.setReason(reason.trim());
    audit.setCreatedAt(Instant.now());
    adjustmentMapper.insert(audit);
  }

  private void requireReason(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "人工调整必须填写原因");
    }
  }
}
