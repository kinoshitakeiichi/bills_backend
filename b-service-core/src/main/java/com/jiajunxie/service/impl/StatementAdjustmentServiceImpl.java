package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.CreditCardStatement;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.entity.StatementAdjustment;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CreditCardStatementMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.mapper.StatementAdjustmentMapper;
import com.jiajunxie.service.StatementAdjustmentService;
import com.jiajunxie.vo.StatementAdjustmentCreateVo;
import com.jiajunxie.vo.StatementAdjustmentSummaryVo;
import com.jiajunxie.vo.StatementAdjustmentVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StatementAdjustmentServiceImpl implements StatementAdjustmentService {

  static final String STATUS_ACTIVE = "ACTIVE";
  static final String TYPE_PAYMENT_DATE = "PAYMENT_DATE";
  static final String STATUS_REVERSED = "REVERSED";
  private static final Set<String> AMOUNT_TYPES = Set.of("FEE", "INTEREST", "CORRECTION", "REFUND", "WAIVER", "OTHER");
  private static final Set<String> ELIGIBLE_STATUSES = Set.of("CONFIRMED", "PARTIALLY_PAID", "PAID");
  private static final Set<String> LEDGER_TYPES = Set.of("FEE", "INTEREST");
  private static final String ORIGIN_STATEMENT_ADJUSTMENT = "STATEMENT_ADJUSTMENT";
  private static final String ORIGIN_STATEMENT_ADJUSTMENT_REVERSAL = "STATEMENT_ADJUSTMENT_REVERSAL";

  private final StatementAdjustmentMapper adjustmentMapper;
  private final CreditCardStatementMapper statementMapper;
  private final TransactionMapper transactionMapper;
  private final TransactionEntryMapper transactionEntryMapper;
  private final AccountMapper accountMapper;


  @Override
  @Transactional
  public StatementAdjustmentVo create(String statementId, StatementAdjustmentCreateVo command) {
    CreditCardStatement statement = requireStatement(statementId);
    requireEligible(statement);
    if (command == null || command.adjustmentTypeCode() == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "账单调整类型不能为空");
    }
    StatementAdjustment item = new StatementAdjustment();
    item.setStatementAdjustmentId(UUID.randomUUID().toString());
    item.setStatementId(statementId);
    item.setAdjustmentTypeCode(command.adjustmentTypeCode());
    item.setReason(command.reason());
    item.setStatusCode(STATUS_ACTIVE);
    Instant now = Instant.now(); item.setCreatedAt(now); item.setUpdatedAt(now);

    if (TYPE_PAYMENT_DATE.equals(command.adjustmentTypeCode())) {
      LocalDate current = summarize(statementId).effectivePaymentDate();
      if (command.newPaymentDate() == null) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "新支付日不能为空");
      if (command.amount() != null && command.amount().signum() != 0) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "支付日调整不能包含金额");
      if (command.oldPaymentDate() != null && !command.oldPaymentDate().equals(current)) throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "账单支付日已变化，请刷新后重试");
      item.setOldPaymentDate(current); item.setNewPaymentDate(command.newPaymentDate());
    } else {
      if (!AMOUNT_TYPES.contains(command.adjustmentTypeCode())) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "不支持的账单调整类型");
      if (command.amount() == null || command.amount().signum() == 0) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "账单调整金额不能为零");
      if (command.currency() == null || !command.currency().equals(statement.getCurrency())) throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "账单调整币种必须与账单一致");
      BigDecimal after = summarize(statementId).adjustedTotalAmount().add(command.amount());
      BigDecimal paid = statement.getPaidAmount() == null ? BigDecimal.ZERO : statement.getPaidAmount();
      if (after.compareTo(paid) < 0) throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "调整后账单总额不能低于已还金额");
      item.setAmount(command.amount()); item.setCurrency(command.currency());
    }
    adjustmentMapper.insert(item);
    if (LEDGER_TYPES.contains(item.getAdjustmentTypeCode())) {
      postLiabilityTransaction(statement, item);
      adjustmentMapper.updateById(item);
    }
    recalculateStatus(statement);
    return toVo(item);
  }

  @Override
  @Transactional
  public StatementAdjustmentVo reverse(String statementId, String adjustmentId) {
    CreditCardStatement statement = requireStatement(statementId); requireEligible(statement);
    StatementAdjustment item = adjustmentMapper.selectById(adjustmentId);
    if (item == null || !statementId.equals(item.getStatementId())) throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "账单调整不存在");
    if (!STATUS_ACTIVE.equals(item.getStatusCode())) throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "账单调整已撤销");
    if (!TYPE_PAYMENT_DATE.equals(item.getAdjustmentTypeCode())) {
      BigDecimal after = summarize(statementId).adjustedTotalAmount().subtract(item.getAmount());
      BigDecimal paid = statement.getPaidAmount() == null ? BigDecimal.ZERO : statement.getPaidAmount();
      if (after.compareTo(paid) < 0) throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "撤销后账单总额不能低于已还金额");
    }
    if (LEDGER_TYPES.contains(item.getAdjustmentTypeCode())) {
      reverseLiabilityTransaction(statement, item);
    }
    item.setStatusCode(STATUS_REVERSED);
    item.setReversedAt(Instant.now());
    item.setUpdatedAt(Instant.now());
    adjustmentMapper.updateById(item);
    recalculateStatus(statement);
    return toVo(item);
  }

  /**
   * Creates an immutable reversal ledger transaction for a system-generated FEE/INTEREST posting.
   * The original transaction remains intact for audit; only the inverse entry changes the account
   * balance.
   */
  private void reverseLiabilityTransaction(
      CreditCardStatement statement, StatementAdjustment adjustment) {
    if (adjustment.getLinkedTransactionId() == null
        || adjustment.getLinkedTransactionId().isBlank()) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "账单调整缺少关联交易，不能撤销");
    }
    if (adjustment.getReversalTransactionId() != null
        && !adjustment.getReversalTransactionId().isBlank()) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "账单调整已经生成冲销交易");
    }

    Transaction original = transactionMapper.selectById(adjustment.getLinkedTransactionId());
    if (original == null
        || !ORIGIN_STATEMENT_ADJUSTMENT.equals(original.getOriginCode())
        || !adjustment.getStatementAdjustmentId().equals(original.getOriginReferenceId())) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "账单调整关联交易不一致，不能撤销");
    }

    Account account = accountMapper.selectById(statement.getCreditAccountId());
    if (account == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "信用卡账户不存在");
    }
    if (!"LIABILITY".equalsIgnoreCase(account.getAccountClassCode())
        || !"CREDIT_CARD".equalsIgnoreCase(account.getAccountTypeCode())) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "账单关联账户不是信用卡负债账户");
    }

    String reversalTransactionId = UUID.randomUUID().toString();
    Instant now = Instant.now();

    Transaction reversal = new Transaction();
    reversal.setTransactionId(reversalTransactionId);
    reversal.setTransactionType(original.getTransactionType());
    reversal.setRemark("冲销：" + (adjustment.getReason() == null ? adjustment.getAdjustmentTypeCode() : adjustment.getReason()));
    reversal.setTransactionTime(now);
    reversal.setOriginCode(ORIGIN_STATEMENT_ADJUSTMENT_REVERSAL);
    reversal.setOriginReferenceId(adjustment.getStatementAdjustmentId());
    reversal.setCreatedAt(now);
    reversal.setUpdatedAt(now);
    transactionMapper.insert(reversal);

    TransactionEntry entry = new TransactionEntry();
    entry.setEntryId(UUID.randomUUID().toString());
    entry.setTransactionId(reversalTransactionId);
    entry.setAccountId(account.getAccountId());
    entry.setAmount(adjustment.getAmount().negate());
    entry.setCurrency(adjustment.getCurrency());
    entry.setEntryRole(
        "FEE".equals(adjustment.getAdjustmentTypeCode()) ? "FEE" : "INTEREST");
    entry.setCreatedAt(now);
    transactionEntryMapper.insert(entry);

    int affected =
        accountMapper.addBalance(account.getAccountId(), adjustment.getAmount().negate());
    if (affected != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "信用卡负债冲销失败");
    }
    adjustment.setReversalTransactionId(reversalTransactionId);
  }

  /**
   * Posts the real liability fact for a FEE/INTEREST adjustment. This deliberately bypasses
   * TransactionService#createTransaction: a statement-origin system transaction must not create a
   * PaymentSchedule, otherwise the amount would be billed again on a future statement.
   */
  private void postLiabilityTransaction(
      CreditCardStatement statement, StatementAdjustment adjustment) {
    Account account = accountMapper.selectById(statement.getCreditAccountId());
    if (account == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "信用卡账户不存在");
    }
    if (!"LIABILITY".equalsIgnoreCase(account.getAccountClassCode())
        || !"CREDIT_CARD".equalsIgnoreCase(account.getAccountTypeCode())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "账单关联账户不是信用卡负债账户");
    }
    if (!statement.getCurrency().equalsIgnoreCase(account.getCurrency())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "账单币种与信用卡账户币种不一致");
    }

    String transactionId = UUID.randomUUID().toString();
    Instant now = Instant.now();

    Transaction transaction = new Transaction();
    transaction.setTransactionId(transactionId);
    transaction.setTransactionType(
        "FEE".equals(adjustment.getAdjustmentTypeCode())
            ? "CREDIT_STATEMENT_FEE"
            : "CREDIT_STATEMENT_INTEREST");
    transaction.setRemark(adjustment.getReason());
    transaction.setTransactionTime(now);
    transaction.setOriginCode(ORIGIN_STATEMENT_ADJUSTMENT);
    transaction.setOriginReferenceId(adjustment.getStatementAdjustmentId());
    transaction.setCreatedAt(now);
    transaction.setUpdatedAt(now);
    transactionMapper.insert(transaction);

    TransactionEntry entry = new TransactionEntry();
    entry.setEntryId(UUID.randomUUID().toString());
    entry.setTransactionId(transactionId);
    entry.setAccountId(account.getAccountId());
    entry.setAmount(adjustment.getAmount());
    entry.setCurrency(adjustment.getCurrency());
    entry.setEntryRole(
        "FEE".equals(adjustment.getAdjustmentTypeCode()) ? "FEE" : "INTEREST");
    entry.setCreatedAt(now);
    transactionEntryMapper.insert(entry);

    int affected = accountMapper.addBalance(account.getAccountId(), adjustment.getAmount());
    if (affected != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "信用卡负债更新失败");
    }
    adjustment.setLinkedTransactionId(transactionId);
  }

  private void requireEligible(CreditCardStatement statement) {
    if (!ELIGIBLE_STATUSES.contains(statement.getStatusCode())) throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "仅已确认或已结算账单允许调整");
  }

  private void recalculateStatus(CreditCardStatement statement) {
    StatementAdjustmentSummaryVo summary = summarize(statement.getStatementId());
    BigDecimal paid = statement.getPaidAmount() == null ? BigDecimal.ZERO : statement.getPaidAmount();
    if (paid.signum() == 0) statement.setStatusCode("CONFIRMED");
    else if (paid.compareTo(summary.adjustedTotalAmount()) < 0) statement.setStatusCode("PARTIALLY_PAID");
    else statement.setStatusCode("PAID");
    statement.setUpdatedAt(Instant.now()); statementMapper.updateById(statement);
  }

  @Override
  public StatementAdjustmentSummaryVo summarize(String statementId) {
    CreditCardStatement statement = requireStatement(statementId);
    List<StatementAdjustment> active = activeAdjustments(statementId);

    BigDecimal postAdjustmentAmount =
        active.stream()
            .filter(item -> !TYPE_PAYMENT_DATE.equals(item.getAdjustmentTypeCode()))
            .map(StatementAdjustment::getAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal total =
        statement.getTotalAmount() == null ? BigDecimal.ZERO : statement.getTotalAmount();
    BigDecimal paid =
        statement.getPaidAmount() == null ? BigDecimal.ZERO : statement.getPaidAmount();
    BigDecimal adjustedTotalAmount = total.add(postAdjustmentAmount);

    LocalDate effectivePaymentDate =
        active.stream()
            .filter(item -> TYPE_PAYMENT_DATE.equals(item.getAdjustmentTypeCode()))
            .filter(item -> item.getNewPaymentDate() != null)
            .reduce((left, right) -> right)
            .map(StatementAdjustment::getNewPaymentDate)
            .orElseGet(() -> baselinePaymentDate(statement));

    return new StatementAdjustmentSummaryVo(
        postAdjustmentAmount,
        adjustedTotalAmount,
        adjustedTotalAmount.subtract(paid),
        effectivePaymentDate,
        list(statementId));
  }

  @Override
  public List<StatementAdjustmentVo> list(String statementId) {
    requireStatement(statementId);
    return adjustmentMapper
        .selectList(
            new LambdaQueryWrapper<StatementAdjustment>()
                .eq(StatementAdjustment::getStatementId, statementId)
                .orderByAsc(StatementAdjustment::getCreatedAt)
                .orderByAsc(StatementAdjustment::getStatementAdjustmentId))
        .stream()
        .map(this::toVo)
        .toList();
  }

  private List<StatementAdjustment> activeAdjustments(String statementId) {
    return adjustmentMapper.selectList(
        new LambdaQueryWrapper<StatementAdjustment>()
            .eq(StatementAdjustment::getStatementId, statementId)
            .eq(StatementAdjustment::getStatusCode, STATUS_ACTIVE)
            .orderByAsc(StatementAdjustment::getCreatedAt)
            .orderByAsc(StatementAdjustment::getStatementAdjustmentId));
  }

  private LocalDate baselinePaymentDate(CreditCardStatement statement) {
    if (statement.getManualPaymentDate() != null) return statement.getManualPaymentDate();
    if (statement.getCalculatedPaymentDate() != null) return statement.getCalculatedPaymentDate();
    return statement.getScheduledPaymentDate();
  }

  private CreditCardStatement requireStatement(String statementId) {
    CreditCardStatement statement = statementMapper.selectById(statementId);
    if (statement == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "信用卡账单不存在");
    }
    return statement;
  }

  private StatementAdjustmentVo toVo(StatementAdjustment item) {
    return new StatementAdjustmentVo(
        item.getStatementAdjustmentId(),
        item.getStatementId(),
        item.getAdjustmentTypeCode(),
        item.getAmount(),
        item.getCurrency(),
        item.getOldPaymentDate(),
        item.getNewPaymentDate(),
        item.getReason(),
        item.getStatusCode(),
        item.getLinkedTransactionId(),
        item.getReversalTransactionId(),
        item.getReversedAt(),
        item.getCreatedAt(),
        item.getUpdatedAt());
  }
}
