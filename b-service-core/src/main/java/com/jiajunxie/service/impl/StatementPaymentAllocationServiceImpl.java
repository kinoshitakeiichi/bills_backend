package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.CreditCardStatement;
import com.jiajunxie.entity.StatementPaymentAllocation;
import com.jiajunxie.mapper.CreditCardStatementMapper;
import com.jiajunxie.mapper.StatementPaymentAllocationMapper;
import com.jiajunxie.service.StatementAdjustmentService;
import com.jiajunxie.service.StatementPaymentAllocationService;
import com.jiajunxie.vo.StatementAdjustmentSummaryVo;
import com.jiajunxie.vo.StatementPaymentAllocationCommandVo;
import com.jiajunxie.vo.StatementPaymentAllocationVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StatementPaymentAllocationServiceImpl
    implements StatementPaymentAllocationService {

  private static final String STATUS_CONFIRMED = "CONFIRMED";
  private static final String STATUS_PARTIALLY_PAID = "PARTIALLY_PAID";
  private static final String STATUS_PAID = "PAID";

  private final StatementPaymentAllocationMapper allocationMapper;
  private final CreditCardStatementMapper statementMapper;
  private final StatementAdjustmentService adjustmentService;

  @Override
  @Transactional
  public List<StatementPaymentAllocationVo> replaceAllocations(
      String paymentTransactionId,
      String targetCreditAccountId,
      BigDecimal paymentAmount,
      String currency,
      List<StatementPaymentAllocationCommandVo> allocations) {

    validateHeader(
        paymentTransactionId, targetCreditAccountId, paymentAmount, currency, allocations);
    validateCommands(paymentAmount, allocations);
    validateStatementOwnership(targetCreditAccountId, allocations);

    List<StatementPaymentAllocation> old =
        findByPaymentTransactionId(paymentTransactionId);
    Set<String> affectedStatementIds = new LinkedHashSet<>();
    old.forEach(allocation -> affectedStatementIds.add(allocation.getStatementId()));

    // Lock every touched statement in a deterministic order before reading outstanding amounts.
    // This prevents two simultaneous payments (or browser retries) from both seeing the same
    // pre-payment outstanding balance.
    Set<String> lockIds = new java.util.TreeSet<>(affectedStatementIds);
    allocations.forEach(command -> lockIds.add(command.statementId()));
    for (String statementId : lockIds) {
      if (statementMapper.selectByIdForUpdate(statementId) == null) {
        throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "信用卡账单不存在");
      }
    }

    allocationMapper.delete(
        new LambdaQueryWrapper<StatementPaymentAllocation>()
            .eq(
                StatementPaymentAllocation::getPaymentTransactionId,
                paymentTransactionId));

    // Removing an old payment may make its previous statements payable again.
    for (String statementId : affectedStatementIds) {
      recalculateStatement(statementId);
    }

    Instant now = Instant.now();
    List<StatementPaymentAllocation> inserted = new ArrayList<>();

    for (StatementPaymentAllocationCommandVo command : allocations) {
      CreditCardStatement statement = requirePayableStatement(command.statementId());

      if (!Objects.equals(
          normalizeCurrency(currency), normalizeCurrency(statement.getCurrency()))) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT, "还款币种与账单币种不一致");
      }

      BigDecimal outstanding = outstanding(statement);
      if (command.allocatedAmount().compareTo(outstanding) > 0) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT, "分配金额超过账单未还金额");
      }

      StatementPaymentAllocation entity = new StatementPaymentAllocation();
      entity.setPaymentAllocationId(UUID.randomUUID().toString());
      entity.setPaymentTransactionId(paymentTransactionId);
      entity.setStatementId(statement.getStatementId());
      entity.setAllocatedAmount(command.allocatedAmount());
      entity.setCurrency(normalizeCurrency(currency));
      entity.setAllocationOrder(
          command.allocationOrder() == null ? 0 : command.allocationOrder());
      entity.setCreatedAt(now);
      allocationMapper.insert(entity);
      inserted.add(entity);
      affectedStatementIds.add(statement.getStatementId());

      // Recalculate immediately so the next allocation sees current outstanding.
      recalculateStatement(statement.getStatementId());
    }

    for (String statementId : affectedStatementIds) {
      recalculateStatement(statementId);
    }

    return inserted.stream().map(this::toVo).toList();
  }

  @Override
  @Transactional
  public void deleteByPaymentTransactionId(String paymentTransactionId) {
    if (paymentTransactionId == null || paymentTransactionId.isBlank()) {
      return;
    }

    List<StatementPaymentAllocation> old =
        findByPaymentTransactionId(paymentTransactionId);
    Set<String> statementIds = new LinkedHashSet<>();
    old.forEach(allocation -> statementIds.add(allocation.getStatementId()));
    for (String statementId : new java.util.TreeSet<>(statementIds)) {
      if (statementMapper.selectByIdForUpdate(statementId) == null) {
        throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "信用卡账单不存在");
      }
    }

    allocationMapper.delete(
        new LambdaQueryWrapper<StatementPaymentAllocation>()
            .eq(
                StatementPaymentAllocation::getPaymentTransactionId,
                paymentTransactionId));

    for (String statementId : statementIds) {
      recalculateStatement(statementId);
    }
  }

  @Override
  public List<StatementPaymentAllocationVo> listByPaymentTransactionId(
      String paymentTransactionId) {
    return findByPaymentTransactionId(paymentTransactionId).stream()
        .map(this::toVo)
        .toList();
  }

  @Override
  public List<StatementPaymentAllocationVo> listByStatementId(String statementId) {
    return allocationMapper
        .selectList(
            new LambdaQueryWrapper<StatementPaymentAllocation>()
                .eq(StatementPaymentAllocation::getStatementId, statementId)
                .orderByAsc(StatementPaymentAllocation::getCreatedAt)
                .orderByAsc(StatementPaymentAllocation::getAllocationOrder))
        .stream()
        .map(this::toVo)
        .toList();
  }

  private void validateHeader(
      String paymentTransactionId,
      String targetCreditAccountId,
      BigDecimal paymentAmount,
      String currency,
      List<StatementPaymentAllocationCommandVo> allocations) {
    if (paymentTransactionId == null || paymentTransactionId.isBlank()) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR, "还款交易 ID 不能为空");
    }
    if (targetCreditAccountId == null || targetCreditAccountId.isBlank()) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR, "目标信用卡账户 ID 不能为空");
    }
    if (paymentAmount == null || paymentAmount.signum() <= 0) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR, "还款金额必须大于 0");
    }
    if (currency == null || currency.isBlank()) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR, "还款币种不能为空");
    }
    if (allocations == null || allocations.isEmpty()) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR, "信用卡还款必须分配到账单");
    }
  }

  private void validateStatementOwnership(
      String targetCreditAccountId,
      List<StatementPaymentAllocationCommandVo> allocations) {
    for (StatementPaymentAllocationCommandVo command : allocations) {
      CreditCardStatement statement = statementMapper.selectById(command.statementId());
      if (statement == null) {
        throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "信用卡账单不存在");
      }
      if (!Objects.equals(
          targetCreditAccountId.trim(), statement.getCreditAccountId())) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT,
            "还款只能分配到目标信用卡账户自己的账单");
      }
    }
  }

  private void validateCommands(
      BigDecimal paymentAmount,
      List<StatementPaymentAllocationCommandVo> allocations) {
    Set<String> statementIds = new LinkedHashSet<>();
    BigDecimal total = BigDecimal.ZERO;

    for (StatementPaymentAllocationCommandVo command : allocations) {
      if (command == null
          || command.statementId() == null
          || command.statementId().isBlank()) {
        throw new BusinessException(
            CommonErrorCode.PARAMETER_ERROR, "账单 ID 不能为空");
      }
      if (!statementIds.add(command.statementId())) {
        throw new BusinessException(
            CommonErrorCode.PARAMETER_ERROR, "同一还款不能重复分配到同一账单");
      }
      if (command.allocatedAmount() == null
          || command.allocatedAmount().signum() <= 0) {
        throw new BusinessException(
            CommonErrorCode.PARAMETER_ERROR, "分配金额必须大于 0");
      }
      total = total.add(command.allocatedAmount());
    }

    if (total.compareTo(paymentAmount) != 0) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR, "账单分配合计必须等于信用卡实际还款金额");
    }
  }

  private CreditCardStatement requirePayableStatement(String statementId) {
    CreditCardStatement statement = statementMapper.selectByIdForUpdate(statementId);
    if (statement == null) {
      throw new BusinessException(
          CommonErrorCode.DATA_NOT_FOUND, "信用卡账单不存在");
    }
    if (!STATUS_CONFIRMED.equals(statement.getStatusCode())
        && !STATUS_PARTIALLY_PAID.equals(statement.getStatusCode())) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "该账单当前不可接收还款分配");
    }
    if (outstanding(statement).signum() <= 0) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "该账单已无未还金额");
    }
    return statement;
  }

  private void recalculateStatement(String statementId) {
    CreditCardStatement statement = statementMapper.selectByIdForUpdate(statementId);
    if (statement == null) {
      throw new BusinessException(
          CommonErrorCode.DATA_NOT_FOUND, "信用卡账单不存在");
    }

    if ("OPEN".equals(statement.getStatusCode())
        || "CANCELLED".equals(statement.getStatusCode())) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "未确认或已取消账单不能进入还款结算");
    }
    StatementAdjustmentSummaryVo adjustmentSummary =
        adjustmentService.summarize(statementId);
    BigDecimal adjustedTotal = adjustmentSummary.adjustedTotalAmount();
    if (adjustedTotal == null || adjustedTotal.signum() < 0) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "调整后账单总额无效，不能进行还款结算");
    }

    List<StatementPaymentAllocation> allocations =
        allocationMapper.selectList(
            new LambdaQueryWrapper<StatementPaymentAllocation>()
                .eq(StatementPaymentAllocation::getStatementId, statementId));

    BigDecimal paid =
        allocations.stream()
            .map(StatementPaymentAllocation::getAllocatedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (paid.compareTo(adjustedTotal) > 0) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "账单已还金额不能超过调整后账单总额");
    }

    statement.setPaidAmount(paid);
    if (paid.signum() == 0) {
      statement.setStatusCode(STATUS_CONFIRMED);
    } else if (paid.compareTo(adjustedTotal) < 0) {
      statement.setStatusCode(STATUS_PARTIALLY_PAID);
    } else {
      statement.setStatusCode(STATUS_PAID);
    }
    statement.setUpdatedAt(Instant.now());
    statementMapper.updateById(statement);
  }

  private BigDecimal outstanding(CreditCardStatement statement) {
    StatementAdjustmentSummaryVo adjustmentSummary =
        adjustmentService.summarize(statement.getStatementId());
    return adjustmentSummary.outstandingAmount();
  }

  private List<StatementPaymentAllocation> findByPaymentTransactionId(
      String paymentTransactionId) {
    return allocationMapper.selectList(
        new LambdaQueryWrapper<StatementPaymentAllocation>()
            .eq(
                StatementPaymentAllocation::getPaymentTransactionId,
                paymentTransactionId)
            .orderByAsc(StatementPaymentAllocation::getAllocationOrder));
  }

  private String normalizeCurrency(String currency) {
    return currency.trim().toUpperCase(Locale.ROOT);
  }

  private StatementPaymentAllocationVo toVo(
      StatementPaymentAllocation allocation) {
    return new StatementPaymentAllocationVo(
        allocation.getPaymentAllocationId(),
        allocation.getPaymentTransactionId(),
        allocation.getStatementId(),
        allocation.getAllocatedAmount(),
        allocation.getCurrency(),
        allocation.getAllocationOrder(),
        allocation.getCreatedAt());
  }
}
