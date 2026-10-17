package com.jiajunxie.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.convert.TransactionConvertServiceMapper;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.Category;
import com.jiajunxie.entity.Person;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionAllocation;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.entity.LoanPaymentDetail;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.mapper.PersonMapper;
import com.jiajunxie.mapper.TransactionAllocationMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.mapper.LoanPaymentDetailMapper;
import com.jiajunxie.model.AccountTransactionRow;
import com.jiajunxie.service.CreditCardPaymentPlanService;
import com.jiajunxie.service.StatementPaymentAllocationService;
import com.jiajunxie.service.TransactionService;
import com.jiajunxie.vo.AccountTransactionMonthVo;
import com.jiajunxie.vo.AccountTransactionVo;
import com.jiajunxie.vo.AllocationVo;
import com.jiajunxie.vo.StatementPaymentAllocationCommandVo;
import com.jiajunxie.vo.TransactionCreateVo;
import com.jiajunxie.vo.TransactionDetailVo;
import com.jiajunxie.vo.TransactionEntryVo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * Application service for the F4.1 transaction engine.
 *
 * <p>{@link Transaction} stores the business event, {@link TransactionEntry} is the balance fact,
 * and {@link Account#getTotalAmount()} is only a rebuildable balance cache. All writes that change
 * entries and balances therefore run in one database transaction.
 */
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {
  private final TransactionMapper transactionMapper;
  private final TransactionEntryMapper transactionEntryMapper;
  private final AccountMapper accountMapper;
  private final TransactionConvertServiceMapper transactionConvertMapper;
  private final TransactionAllocationMapper transactionAllocationMapper;
  private final PersonMapper personMapper;
  private final CategoryMapper categoryMapper;
  private final CreditCardPaymentPlanService paymentPlanService;
  private final StatementPaymentAllocationService statementPaymentAllocationService;
  private final LoanPaymentDetailMapper loanPaymentDetailMapper;
  private final TransactionMutationPolicy mutationPolicy;
  private final ForeignCurrencyPositionEngine foreignCurrencyPositionEngine;

  @Override
  public TransactionDetailVo getTransaction(String transactionId) {
    requireText(transactionId, "交易 ID 不能为空");

    Transaction transaction = transactionMapper.selectById(transactionId);
    if (transaction == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "交易不存在");
    }

    List<TransactionEntry> entries = transactionEntryMapper.selectByTransactionId(transactionId);
    List<TransactionAllocation> allocations =
        transactionAllocationMapper.selectByTransactionId(transactionId);
    return transactionConvertMapper.toDetailVo(
        transaction,
        transactionConvertMapper.toEntryVos(entries),
        transactionConvertMapper.toAllocationVos(allocations),
        paymentPlanService.getPlan(transactionId));
  }

  /**
   * Creates the event, entries, ownership record and balance deltas atomically. Validation is
   * completed before the first persistent write whenever possible.
   */
  @Override
  @Transactional
  public String createTransaction(TransactionCreateVo transactionVo) {
    validateTransaction(transactionVo);
    lockAccountRows(List.of(), transactionVo.entries());

    String transactionId = UUID.randomUUID().toString();
    Transaction transaction = buildTransaction(transactionId, transactionVo, null);
    transactionMapper.insert(transaction);

    List<TransactionEntry> newEntries = applyNewEntries(transactionId, transactionVo.entries());
    foreignCurrencyPositionEngine.apply(
        transactionId, transactionVo.transactionType(), transactionVo.transactionTime(), newEntries);
    applyAllocations(transactionId, transactionVo.allocations());
    paymentPlanService.createPlan(transactionId, transactionVo);
    applyStatementPaymentAllocations(transactionId, transactionVo);
    return transactionId;
  }

  /**
   * Replaces a transaction safely: first reverse old balance deltas, remove old dependent rows,
   * update the event and finally apply the new entries. A failure at any step rolls back
   * everything.
   */
  @Override
  @Transactional
  public Integer updateTransaction(String transactionId, TransactionCreateVo transactionVo) {
    requireText(transactionId, "交易 ID 不能为空");
    validateTransaction(transactionVo);

    Transaction existing = transactionMapper.selectById(transactionId);
    if (existing == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "交易不存在");
    }
    assertUserMutable(existing);

    List<TransactionEntry> oldEntries = transactionEntryMapper.selectByTransactionId(transactionId);
    lockAccountRows(oldEntries, transactionVo.entries());

    if (paymentPlanService.isLocked(transactionId)) {
      assertLockedFinancialFieldsUnchanged(existing, oldEntries, transactionVo);

      // CONFIRMED statement snapshots must stay immutable. Only descriptive/analysis dimensions
      // (category, merchant, remark and allocations) may change after billing confirmation.
      Transaction replacement =
          buildTransaction(transactionId, transactionVo, existing.getCreatedAt());
      int affectedRows = transactionMapper.updateById(replacement);
      transactionAllocationMapper.deleteByTransactionId(transactionId);
      applyAllocations(transactionId, transactionVo.allocations());
      return affectedRows;
    }

    // Allocation settlement is part of the same transaction boundary. Removing the old mapping
    // first recalculates the previously affected statements; any later failure rolls it all back.
    statementPaymentAllocationService.deleteByPaymentTransactionId(transactionId);

    foreignCurrencyPositionEngine.assertLegacyMutationSafe(transactionId, oldEntries);
    foreignCurrencyPositionEngine.reverse(transactionId);
    rollbackBalances(oldEntries);
    transactionEntryMapper.deleteByTransactionId(transactionId);
    transactionAllocationMapper.deleteByTransactionId(transactionId);

    Transaction replacement =
        buildTransaction(transactionId, transactionVo, existing.getCreatedAt());
    int affectedRows = transactionMapper.updateById(replacement);

    List<TransactionEntry> newEntries = applyNewEntries(transactionId, transactionVo.entries());
    foreignCurrencyPositionEngine.apply(
        transactionId, transactionVo.transactionType(), transactionVo.transactionTime(), newEntries);
    applyAllocations(transactionId, transactionVo.allocations());
    paymentPlanService.replacePlan(transactionId, transactionVo);
    applyStatementPaymentAllocations(transactionId, transactionVo);
    return affectedRows;
  }

  private void assertLockedFinancialFieldsUnchanged(
      Transaction existing, List<TransactionEntry> oldEntries, TransactionCreateVo replacement) {
    if (!Objects.equals(
            normalize(existing.getTransactionType()), normalize(replacement.transactionType()))
        || !Objects.equals(existing.getTransactionTime(), replacement.transactionTime())
        || !sameEntries(oldEntries, replacement.entries())
        || !samePaymentPlan(existing.getTransactionId(), replacement)) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "该信用卡消费已进入已确认账单，只能修改分类、商户、备注和归属人");
    }
  }

  private boolean sameEntries(
      List<TransactionEntry> persisted, List<TransactionEntryVo> requested) {
    if (persisted == null || requested == null || persisted.size() != requested.size())
      return false;
    for (int i = 0; i < persisted.size(); i++) {
      TransactionEntry left = persisted.get(i);
      TransactionEntryVo right = requested.get(i);
      if (!Objects.equals(left.getAccountId(), right.accountId())
          || !Objects.equals(normalize(left.getCurrency()), normalize(right.currency()))
          || !Objects.equals(normalize(left.getEntryRole()), normalize(right.entryRole()))
          || left.getAmount() == null
          || right.amount() == null
          || left.getAmount().compareTo(right.amount()) != 0) {
        return false;
      }
    }
    return true;
  }

  private boolean samePaymentPlan(String transactionId, TransactionCreateVo replacement) {
    var plan = paymentPlanService.getPlan(transactionId);
    if (plan == null) return replacement.payment() == null;
    var payment = replacement.payment();
    if (payment == null) return false;
    BigDecimal requestedFee = payment.feeAmount() == null ? BigDecimal.ZERO : payment.feeAmount();
    boolean same = Objects.equals(normalize(plan.paymentTypeCode()), normalize(payment.paymentTypeCode()))
        && Objects.equals(trimToNull(plan.paymentRuleId()), trimToNull(payment.paymentRuleId()))
        && Objects.equals(plan.installmentCount(), payment.installmentCount())
        && plan.feeAmount() != null
        && plan.feeAmount().compareTo(requestedFee) == 0
        && Objects.equals(
            normalize(plan.remainderRuleCode()), normalize(payment.remainderRuleCode()));
    if (!same) return false;
    if (!"MANUAL".equals(normalize(plan.remainderRuleCode()))) return true;
    if (payment.manualPrincipalAmounts() == null || payment.manualFeeAmounts() == null) return false;
    if (plan.schedules().size() != payment.manualPrincipalAmounts().size()
        || plan.schedules().size() != payment.manualFeeAmounts().size()) return false;
    for (int i = 0; i < plan.schedules().size(); i++) {
      var schedule = plan.schedules().get(i);
      BigDecimal principal = payment.manualPrincipalAmounts().get(i);
      BigDecimal fee = payment.manualFeeAmounts().get(i);
      if (principal == null || fee == null
          || schedule.scheduledPrincipalAmount().compareTo(principal) != 0
          || schedule.scheduledFeeAmount().compareTo(fee) != 0) return false;
    }
    return true;
  }

  /** Reverses every old entry before deleting the transaction and its dependent records. */
  @Override
  @Transactional
  public Integer removeTransaction(String transactionId) {
    requireText(transactionId, "交易 ID 不能为空");

    Transaction existing = transactionMapper.selectById(transactionId);
    if (existing == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "交易不存在或已删除");
    }
    assertUserMutable(existing);
    assertNotReferencedByRefund(transactionId);

    paymentPlanService.assertMutable(transactionId);

    List<TransactionEntry> oldEntries = transactionEntryMapper.selectByTransactionId(transactionId);
    lockAccountRows(oldEntries, List.of());

    statementPaymentAllocationService.deleteByPaymentTransactionId(transactionId);
    foreignCurrencyPositionEngine.assertLegacyMutationSafe(transactionId, oldEntries);
    foreignCurrencyPositionEngine.reverse(transactionId);
    rollbackBalances(oldEntries);
    transactionEntryMapper.deleteByTransactionId(transactionId);
    transactionAllocationMapper.deleteByTransactionId(transactionId);
    paymentPlanService.removePlan(transactionId);

    return transactionMapper.deleteById(transactionId);
  }

  private void assertNotReferencedByRefund(String transactionId) {
    if (transactionMapper.countRefundReferences(transactionId) > 0) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "该交易已有退款记录，不能删除原交易；如需更正请先处理关联退款");
    }
  }

  private void assertUserMutable(Transaction transaction) {
    if (transaction == null) {
      return;
    }
    String readonlyReason = mutationPolicy.lifecycleReadonlyReason(transaction);
    if (readonlyReason != null) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, readonlyReason);
    }
  }

  /** Rebuilds the account balance cache from the immutable entry facts. */
  @Override
  @Transactional
  public BigDecimal recalculateBalance(String accountId) {
    Account account = requireAccount(accountId);
    BigDecimal calculated = transactionEntryMapper.sumAmountByAccountId(accountId);
    BigDecimal balance = calculated == null ? BigDecimal.ZERO : calculated;

    int affectedRows = accountMapper.replaceBalance(account.getAccountId(), balance);
    if (affectedRows != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "账户余额重算失败");
    }
    return balance;
  }

  @Override
  public AccountTransactionMonthVo listAccountTransactions(
      String accountId, YearMonth month, String keyword, ZoneId timezone) {
    Account account = requireAccount(accountId);

    ZoneId queryTimezone = timezone == null ? ZoneId.of("Asia/Tokyo") : timezone;
    YearMonth queryMonth = month == null ? YearMonth.now(queryTimezone) : month;

    Instant startInclusive = queryMonth.atDay(1).atStartOfDay(queryTimezone).toInstant();
    Instant endExclusive =
        queryMonth.plusMonths(1).atDay(1).atStartOfDay(queryTimezone).toInstant();

    List<AccountTransactionRow> rows =
        transactionMapper.selectAccountTransactions(
            accountId, startInclusive, endExclusive, trimToNull(keyword));
    enrichLoanPaymentDetails(rows);
    List<AccountTransactionVo> transactions =
        transactionConvertMapper.toAccountTransactionVos(rows);

    BigDecimal totalIncome = BigDecimal.ZERO;
    BigDecimal totalExpense = BigDecimal.ZERO;
    BigDecimal netChange = BigDecimal.ZERO;

    boolean liabilityAccount = "LIABILITY".equalsIgnoreCase(account.getAccountClassCode());

    for (AccountTransactionVo transaction : transactions) {
      BigDecimal amount = transaction.amount();
      netChange = netChange.add(amount);

      String transactionType = normalize(transaction.transactionType());
      BigDecimal displayAmount = amount.abs();

      if (liabilityAccount) {
        // 对负债账户：totalExpense 表示本月净新增负债，totalIncome 只表示实际还款。
        if ("CREDIT_REFUND".equals(transactionType)) {
          totalExpense = totalExpense.subtract(displayAmount);
        } else if (isLiabilityIncrease(transactionType)) {
          totalExpense = totalExpense.add(displayAmount);
        } else if (isLiabilityDecrease(transactionType)) {
          totalIncome = totalIncome.add(displayAmount);
        }
      } else {
        // 对资产账户：只统计真实收入与支出，转账和还款不进入收支汇总。
        if ("REFUND".equals(transactionType)) {
          totalExpense = totalExpense.subtract(displayAmount);
        } else if (isAssetIncome(transactionType)) {
          totalIncome = totalIncome.add(displayAmount);
        } else if (isAssetExpense(transactionType)) {
          totalExpense = totalExpense.add(displayAmount);
        }
      }
    }

    return new AccountTransactionMonthVo(
        accountId,
        queryMonth.toString(),
        queryTimezone.getId(),
        totalIncome,
        totalExpense,
        netChange,
        transactions);
  }

  private void enrichLoanPaymentDetails(List<AccountTransactionRow> rows) {
    if (rows == null || rows.isEmpty()) return;

    List<String> paymentTransactionIds =
        rows.stream()
            .filter(row -> "LOAN_PAYMENT".equals(normalize(row.getTransactionType())))
            .map(AccountTransactionRow::getTransactionId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (paymentTransactionIds.isEmpty()) return;

    Map<String, LoanPaymentDetail> detailsByTransactionId =
        loanPaymentDetailMapper.selectByTransactionIds(paymentTransactionIds).stream()
            .collect(java.util.stream.Collectors.toMap(
                LoanPaymentDetail::getTransactionId, Function.identity()));

    for (AccountTransactionRow row : rows) {
      LoanPaymentDetail detail = detailsByTransactionId.get(row.getTransactionId());
      if (detail == null) continue;
      row.setLoanPrincipalAmount(detail.getPrincipalAmount());
      row.setLoanInterestAmount(detail.getInterestAmount());
      row.setLoanFeeAmount(detail.getFeeAmount());
      row.setLoanTotalPaymentAmount(
          detail.getPrincipalAmount().add(detail.getInterestAmount()).add(detail.getFeeAmount()));
    }
  }

  private Transaction buildTransaction(
      String transactionId, TransactionCreateVo transactionVo, Instant createdAt) {
    Transaction transaction = transactionConvertMapper.toEntity(transactionVo);
    Instant now = Instant.now();
    transaction.setTransactionId(transactionId);
    transaction.setCreatedAt(createdAt == null ? now : createdAt);
    transaction.setUpdatedAt(now);
    return transaction;
  }

  /**
   * Serializes ASSET/account mutations in stable account-id order. Besides reducing transfer
   * deadlocks, this is the cutover hand-off barrier: a transaction that was waiting behind V3
   * cutover must observe the committed marker before choosing the FX lifecycle engine.
   */
  private void lockAccountRows(
      List<TransactionEntry> persistedEntries, List<TransactionEntryVo> requestedEntries) {
    java.util.TreeSet<String> accountIds = new java.util.TreeSet<>();
    if (persistedEntries != null) {
      for (TransactionEntry entry : persistedEntries) {
        if (entry != null && entry.getAccountId() != null) accountIds.add(entry.getAccountId());
      }
    }
    if (requestedEntries != null) {
      for (TransactionEntryVo entry : requestedEntries) {
        if (entry != null && entry.accountId() != null) accountIds.add(entry.accountId());
      }
    }
    for (String accountId : accountIds) accountMapper.lockById(accountId);
  }

  /**
   * Persists validated entries and applies their signed amounts to account balance caches. Currency
   * equality is checked against the referenced account before any entry is inserted.
   */
  private List<TransactionEntry> applyNewEntries(
      String transactionId, List<TransactionEntryVo> entries) {
    Map<String, Account> accounts = new HashMap<>();

    // Validate every referenced account and currency before persisting any entry.
    // The surrounding transaction still guarantees rollback, while this avoids doing avoidable
    // writes
    // before discovering that a later entry is invalid.
    for (TransactionEntryVo entryVo : entries) {
      Account account = accounts.computeIfAbsent(entryVo.accountId(), this::requireAccount);
      String accountCurrency = normalize(account.getCurrency());
      String entryCurrency = normalize(entryVo.currency());
      if (!accountCurrency.equals(entryCurrency)) {
        throw new BusinessException(
            CommonErrorCode.PARAMETER_ERROR, "分录币种与账户币种不一致：" + account.getAccountName());
      }
    }

    Instant createdAt = Instant.now();
    List<TransactionEntry> persistedEntries = new java.util.ArrayList<>();
    for (TransactionEntryVo entryVo : entries) {
      TransactionEntry entry = transactionConvertMapper.toEntryEntity(entryVo);
      entry.setEntryId(UUID.randomUUID().toString());
      entry.setTransactionId(transactionId);
      entry.setCreatedAt(createdAt);
      transactionEntryMapper.insert(entry);
      addBalanceOrFail(entry.getAccountId(), entry.getAmount());
      persistedEntries.add(entry);
    }
    return persistedEntries;
  }

  /** Persists validated person allocations for analysis dimensions. */
  private void applyAllocations(String transactionId, List<AllocationVo> allocations) {
    if (allocations == null || allocations.isEmpty()) return;
    Set<String> persons = new HashSet<>();
    Instant createdAt = Instant.now();
    for (AllocationVo allocation : allocations) {
      requireText(allocation.personId(), "分摊人员不能为空");
      if (!persons.add(allocation.personId())) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "同一人员不能重复分摊");
      }
      Person person = personMapper.selectById(allocation.personId());
      if (person == null || !Boolean.TRUE.equals(person.getEnabled())) {
        throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "分摊人员不存在或已停用");
      }
      if (allocation.allocatedAmount() == null || allocation.allocatedAmount().signum() < 0) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "分摊金额不能小于零");
      }
      TransactionAllocation entity = new TransactionAllocation();
      entity.setAllocationId(UUID.randomUUID().toString());
      entity.setTransactionId(transactionId);
      entity.setPersonId(allocation.personId());
      entity.setAllocatedAmount(allocation.allocatedAmount());
      entity.setCurrency(normalize(allocation.currency()));
      entity.setAllocationRatio(allocation.allocationRatio());
      entity.setCreatedAt(createdAt);
      transactionAllocationMapper.insert(entity);
    }
  }

  /** Applies the inverse of every old entry, restoring balances before update/delete. */
  private void rollbackBalances(List<TransactionEntry> oldEntries) {
    for (TransactionEntry oldEntry : oldEntries) {
      addBalanceOrFail(oldEntry.getAccountId(), oldEntry.getAmount().negate());
    }
  }

  private void addBalanceOrFail(String accountId, BigDecimal delta) {
    int affectedRows = accountMapper.addBalance(accountId, delta);
    if (affectedRows != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "账户余额更新失败，账户可能已被删除：" + accountId);
    }
  }

  private Account requireAccount(String accountId) {
    requireText(accountId, "账户 ID 不能为空");
    Account account = accountMapper.selectById(accountId);
    if (account == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "账户不存在：" + accountId);
    }
    return account;
  }

  /**
   * Performs server-side accounting validation. Front-end filtering is only a usability aid and
   * must never be trusted as the source of financial integrity.
   */
  private void validateTransaction(TransactionCreateVo transactionVo) {
    if (transactionVo == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "交易数据不能为空");
    }
    requireText(transactionVo.transactionType(), "交易类型不能为空");
    if (transactionVo.transactionTime() == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "交易时间不能为空");
    }
    if (transactionVo.entries() == null || transactionVo.entries().isEmpty()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "交易至少需要一条分录");
    }

    Set<String> uniqueEntryKeys = new HashSet<>();
    for (TransactionEntryVo entry : transactionVo.entries()) {
      if (entry == null) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "交易分录不能为空");
      }
      requireText(entry.accountId(), "分录账户 ID 不能为空");
      requireText(entry.currency(), "分录币种不能为空");
      requireText(entry.entryRole(), "分录角色不能为空");
      if (entry.amount() == null || entry.amount().compareTo(BigDecimal.ZERO) == 0) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "分录金额不能为零");
      }

      String key = entry.accountId() + "|" + normalize(entry.entryRole());
      if (!uniqueEntryKeys.add(key)) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "同一账户与分录角色不能重复，请先合并金额");
      }
    }

    validateF4BusinessRules(transactionVo);
    validateStatementPaymentAllocationRequest(transactionVo);
    paymentPlanService.validatePayment(transactionVo);
  }

  private void validateF4BusinessRules(TransactionCreateVo transactionVo) {
    String type = normalize(transactionVo.transactionType());
    if (!Set.of("INCOME", "REFUND", "EXPENSE", "TRANSFER", "CREDIT_PURCHASE", "CREDIT_PAYMENT")
        .contains(type)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "F4 暂不支持该交易类型：" + type);
    }

    Map<String, Account> accounts = new HashMap<>();
    for (TransactionEntryVo entry : transactionVo.entries()) {
      accounts.computeIfAbsent(entry.accountId(), this::requireEnabledAccount);
    }

    switch (type) {
      case "INCOME" -> {
        validateSingleEntry(transactionVo.entries(), accounts, true, "INCOME");
        validateCategory(transactionVo.categoryId(), "INCOME", true);
        validateAllocations(transactionVo.allocations(), transactionVo.entries(), true);
      }
      case "REFUND" -> {
        validateSingleEntry(transactionVo.entries(), accounts, true, "REFUND");
        validateCategory(transactionVo.categoryId(), "EXPENSE", true);
        validateAllocations(transactionVo.allocations(), transactionVo.entries(), true);
      }
      case "EXPENSE" -> {
        validateSingleEntry(transactionVo.entries(), accounts, false, "EXPENSE");
        validateCategory(transactionVo.categoryId(), "EXPENSE", true);
        validateAllocations(transactionVo.allocations(), transactionVo.entries(), true);
      }
      case "CREDIT_PURCHASE" -> {
        validateCreditPurchase(transactionVo.entries(), accounts);
        validateCategory(transactionVo.categoryId(), "EXPENSE", true);
        validateAllocations(transactionVo.allocations(), transactionVo.entries(), true);
      }
      case "TRANSFER" -> {
        validateTransfer(transactionVo.entries(), accounts, false);
        validateCategory(transactionVo.categoryId(), null, false);
        validateAllocations(transactionVo.allocations(), transactionVo.entries(), false);
      }
      case "CREDIT_PAYMENT" -> {
        validateTransfer(transactionVo.entries(), accounts, true);
        validateCategory(transactionVo.categoryId(), null, false);
        validateAllocations(transactionVo.allocations(), transactionVo.entries(), false);
      }
      default -> throw new IllegalStateException("Unsupported transaction type: " + type);
    }
  }

  private void validateStatementPaymentAllocationRequest(TransactionCreateVo transactionVo) {
    String type = normalize(transactionVo.transactionType());
    List<StatementPaymentAllocationCommandVo> paymentAllocations =
        transactionVo.paymentAllocations();

    if (!"CREDIT_PAYMENT".equals(type)) {
      if (paymentAllocations != null && !paymentAllocations.isEmpty()) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "只有信用卡还款可以设置账单分配");
      }
      return;
    }

    if (paymentAllocations == null || paymentAllocations.isEmpty()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "信用卡还款必须分配到已确认账单");
    }
  }

  private void applyStatementPaymentAllocations(
      String transactionId, TransactionCreateVo transactionVo) {
    if (!"CREDIT_PAYMENT".equals(normalize(transactionVo.transactionType()))) {
      return;
    }

    TransactionEntryVo creditCardEntry =
        transactionVo.entries().stream()
            .filter(entry -> "TARGET".equals(normalize(entry.entryRole())))
            .findFirst()
            .orElseThrow(
                () -> new BusinessException(CommonErrorCode.PARAMETER_ERROR, "信用卡还款缺少 TARGET 分录"));

    statementPaymentAllocationService.replaceAllocations(
        transactionId,
        creditCardEntry.accountId(),
        creditCardEntry.amount().abs(),
        creditCardEntry.currency(),
        transactionVo.paymentAllocations());
  }

  private void validateSingleEntry(
      List<TransactionEntryVo> entries,
      Map<String, Account> accounts,
      boolean positive,
      String type) {
    if (entries.size() != 1) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, type + " 必须且只能生成一条分录");
    }
    TransactionEntryVo entry = entries.get(0);
    Account account = accounts.get(entry.accountId());
    if (!"ASSET".equals(normalize(account.getAccountClassCode()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, type + " 只能选择资产账户");
    }
    if (!"SINGLE".equals(normalize(entry.entryRole()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, type + " 的分录角色必须是 SINGLE");
    }
    if ((positive && entry.amount().signum() <= 0) || (!positive && entry.amount().signum() >= 0)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, type + " 的金额方向不正确");
    }
  }

  private void validateCreditPurchase(
      List<TransactionEntryVo> entries, Map<String, Account> accounts) {
    if (entries.size() != 1) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "信用卡消费必须且只能生成一条分录");
    }
    TransactionEntryVo entry = entries.get(0);
    Account account = accounts.get(entry.accountId());
    if (!"LIABILITY".equals(normalize(account.getAccountClassCode()))
        || !"CREDIT_CARD".equals(normalize(account.getAccountTypeCode()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "信用卡消费只能选择信用卡账户");
    }
    if (!"SINGLE".equals(normalize(entry.entryRole())) || entry.amount().signum() <= 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "信用卡消费分录必须为 SINGLE 且金额为正");
    }
  }

  private void validateTransfer(
      List<TransactionEntryVo> entries, Map<String, Account> accounts, boolean creditPayment) {
    if (entries.size() != 2) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "双账户交易必须生成两条分录");
    }
    TransactionEntryVo source =
        entries.stream()
            .filter(entry -> "SOURCE".equals(normalize(entry.entryRole())))
            .findFirst()
            .orElseThrow(
                () -> new BusinessException(CommonErrorCode.PARAMETER_ERROR, "缺少 SOURCE 分录"));
    TransactionEntryVo target =
        entries.stream()
            .filter(entry -> "TARGET".equals(normalize(entry.entryRole())))
            .findFirst()
            .orElseThrow(
                () -> new BusinessException(CommonErrorCode.PARAMETER_ERROR, "缺少 TARGET 分录"));
    if (source.accountId().equals(target.accountId())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "来源账户和目标账户不能相同");
    }
    Account sourceAccount = accounts.get(source.accountId());
    Account targetAccount = accounts.get(target.accountId());
    if (!"ASSET".equals(normalize(sourceAccount.getAccountClassCode()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "来源账户必须是资产账户");
    }
    if (!normalize(sourceAccount.getCurrency()).equals(normalize(targetAccount.getCurrency()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "F4 暂不支持跨币种交易");
    }
    if (source.amount().signum() >= 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "SOURCE 分录金额必须为负");
    }
    if (creditPayment) {
      if (!"LIABILITY".equals(normalize(targetAccount.getAccountClassCode()))
          || !"CREDIT_CARD".equals(normalize(targetAccount.getAccountTypeCode()))
          || target.amount().signum() >= 0) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "信用卡还款目标必须是信用卡且金额为负");
      }
    } else {
      if (!"ASSET".equals(normalize(targetAccount.getAccountClassCode()))
          || target.amount().signum() <= 0) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "转账目标必须是资产账户且金额为正");
      }
    }
    if (source.amount().abs().compareTo(target.amount().abs()) != 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "双账户交易两侧金额必须一致");
    }
  }

  private void validateCategory(String categoryId, String expectedType, boolean required) {
    String normalizedId = trimToNull(categoryId);
    if (normalizedId == null) {
      if (required) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "该交易类型必须选择分类");
      }
      return;
    }
    if (!required) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "该交易类型不能选择分类");
    }
    Category category = categoryMapper.selectById(normalizedId);
    if (category == null || !Boolean.TRUE.equals(category.getEnabled())) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "分类不存在或已停用");
    }
    if (!normalize(expectedType).equals(normalize(category.getCategoryTypeCode()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "分类类型与交易类型不匹配");
    }
    Long childCount =
        categoryMapper.selectCount(
            Wrappers.<Category>lambdaQuery()
                .eq(Category::getParentId, normalizedId)
                .eq(Category::getEnabled, true));
    if (childCount != null && childCount > 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "只能选择叶子分类");
    }
  }

  private void validateAllocations(
      List<AllocationVo> allocations, List<TransactionEntryVo> entries, boolean allowed) {
    int size = allocations == null ? 0 : allocations.size();
    if (!allowed && size > 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "该交易类型不需要人员分摊");
    }
    if (size == 0) return;

    BigDecimal expectedAmount = entries.get(0).amount().abs();
    String expectedCurrency = normalize(entries.get(0).currency());
    BigDecimal allocatedTotal = BigDecimal.ZERO;
    BigDecimal ratioTotal = BigDecimal.ZERO;
    boolean hasRatio = false;
    boolean hasMissingRatio = false;
    Set<String> personIds = new HashSet<>();

    for (AllocationVo allocation : allocations) {
      requireText(allocation.personId(), "分摊人员不能为空");
      if (!personIds.add(allocation.personId())) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "同一人员不能重复分摊");
      }
      Person person = personMapper.selectById(allocation.personId());
      if (person == null || !Boolean.TRUE.equals(person.getEnabled())) {
        throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "分摊人员不存在或已停用");
      }

      if (allocation.allocatedAmount() == null || allocation.allocatedAmount().signum() <= 0) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "分摊金额必须大于零");
      }
      if (!expectedCurrency.equals(normalize(allocation.currency()))) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "分摊币种必须与交易币种一致");
      }
      allocatedTotal = allocatedTotal.add(allocation.allocatedAmount());

      if (allocation.allocationRatio() == null) {
        hasMissingRatio = true;
      } else {
        hasRatio = true;
        if (allocation.allocationRatio().signum() <= 0
            || allocation.allocationRatio().compareTo(BigDecimal.ONE) > 0) {
          throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "分摊比例必须大于 0 且不超过 1");
        }
        ratioTotal = ratioTotal.add(allocation.allocationRatio());
      }
    }

    if (allocatedTotal.compareTo(expectedAmount) != 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "人员分摊金额合计必须等于交易金额");
    }
    if (hasRatio && hasMissingRatio) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "分摊比例必须全部填写或全部留空");
    }
    if (hasRatio && ratioTotal.compareTo(BigDecimal.ONE) != 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "人员分摊比例合计必须等于 1");
    }
  }

  private Account requireEnabledAccount(String accountId) {
    Account account = requireAccount(accountId);
    if (!Boolean.TRUE.equals(account.getEnabled())) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "账户已停用：" + accountId);
    }
    return account;
  }

  private static boolean isAssetIncome(String transactionType) {
    return "INCOME".equals(transactionType);
  }

  private static boolean isAssetExpense(String transactionType) {
    return "EXPENSE".equals(transactionType);
  }

  private static boolean isLiabilityIncrease(String transactionType) {
    return "CREDIT_PURCHASE".equals(transactionType)
        || "LOAN_DISBURSEMENT".equals(transactionType)
        || "LOAN_PURCHASE".equals(transactionType);
  }

  private static boolean isLiabilityDecrease(String transactionType) {
    return "CREDIT_PAYMENT".equals(transactionType)
        || "LOAN_PAYMENT".equals(transactionType);
  }

  private static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, message);
    }
  }

  private static String normalize(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
