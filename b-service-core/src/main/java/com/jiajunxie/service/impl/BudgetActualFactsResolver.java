package com.jiajunxie.service.impl;

import com.jiajunxie.domain.budget.BudgetActualContract;
import com.jiajunxie.domain.budget.BudgetActualFacts;
import com.jiajunxie.domain.budget.BudgetDimensionSourceContract;
import com.jiajunxie.domain.budget.BudgetTargetMatcherContract;
import com.jiajunxie.entity.Category;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionAllocation;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.mapper.TransactionAllocationMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BudgetActualFactsResolver {
  private static final BudgetActualContract ACTUAL_CONTRACT = new BudgetActualContract();
  private static final BudgetDimensionSourceContract DIMENSION_SOURCE =
      new BudgetDimensionSourceContract();

  private final TransactionMapper transactionMapper;
  private final TransactionEntryMapper entryMapper;
  private final TransactionAllocationMapper allocationMapper;
  private final CategoryMapper categoryMapper;

  public BudgetActualFacts resolve(String transactionId) {
    if (transactionId == null || transactionId.isBlank()) {
      throw new IllegalArgumentException("transactionId is required");
    }

    Transaction transaction = requireTransaction(transactionId.trim());
    BudgetActualContract.ActualEffect effect =
        ACTUAL_CONTRACT.classify(transaction.getTransactionType(), transaction.getOriginCode());

    if (effect == BudgetActualContract.ActualEffect.UNRESOLVED) {
      // Reuse the contract's fail-closed message instead of silently constructing ambiguous facts.
      ACTUAL_CONTRACT.signedActual(
          transaction.getTransactionType(), transaction.getOriginCode(), BigDecimal.ONE);
    }

    List<TransactionEntry> currentEntries = entryMapper.selectByTransactionId(transaction.getTransactionId());
    AmountCurrency amountCurrency = resolveAmountCurrency(effect, currentEntries);

    String dimensionSourceId =
        DIMENSION_SOURCE.dimensionSourceTransactionId(
            transaction.getTransactionId(),
            transaction.getTransactionType(),
            transaction.getOriginReferenceId());
    Transaction dimensionSource =
        transaction.getTransactionId().equals(dimensionSourceId)
            ? transaction
            : requireTransaction(dimensionSourceId);

    Set<String> categoryAncestorIds = resolveCategoryAncestors(dimensionSource.getCategoryId());
    Set<String> currentAccountIds = new LinkedHashSet<>();
    for (TransactionEntry entry : currentEntries) {
      if (entry.getAccountId() != null && !entry.getAccountId().isBlank()) {
        currentAccountIds.add(entry.getAccountId().trim());
      }
    }

    // Person actual must come from the current transaction. In particular, CREDIT_REFUND has
    // proportional refund allocations (e.g. 18/12), while the original purchase may be 60/40.
    Map<String, BigDecimal> currentPersonAmounts = new LinkedHashMap<>();
    for (TransactionAllocation allocation :
        allocationMapper.selectByTransactionId(transaction.getTransactionId())) {
      if (allocation.getPersonId() == null || allocation.getPersonId().isBlank()) continue;
      BigDecimal amount = allocation.getAllocatedAmount();
      if (amount == null) continue;
      currentPersonAmounts.merge(allocation.getPersonId().trim(), amount.abs(), BigDecimal::add);
    }

    BudgetTargetMatcherContract.Facts targetFacts =
        new BudgetTargetMatcherContract.Facts(
            dimensionSource.getCategoryId(),
            categoryAncestorIds,
            currentAccountIds,
            dimensionSource.getMerchant(),
            currentPersonAmounts);

    return new BudgetActualFacts(
        transaction.getTransactionId(),
        transaction.getTransactionType(),
        transaction.getOriginCode(),
        transaction.getOriginReferenceId(),
        transaction.getTransactionTime(),
        amountCurrency.amount(),
        amountCurrency.currency(),
        targetFacts);
  }


  private Transaction requireTransaction(String transactionId) {
    Transaction transaction = transactionMapper.selectById(transactionId);
    if (transaction == null) {
      throw new IllegalStateException("预算事实引用的交易不存在：" + transactionId);
    }
    return transaction;
  }

  private AmountCurrency resolveAmountCurrency(
      BudgetActualContract.ActualEffect effect, List<TransactionEntry> entries) {
    if (effect == BudgetActualContract.ActualEffect.EXCLUDED) {
      return new AmountCurrency(BigDecimal.ZERO, null);
    }
    if (entries == null || entries.size() != 1) {
      throw new IllegalStateException("预算可计入交易必须且只能有一条余额分录");
    }
    TransactionEntry entry = entries.get(0);
    if (entry.getAmount() == null || entry.getCurrency() == null || entry.getCurrency().isBlank()) {
      throw new IllegalStateException("预算可计入交易的金额或币种事实不完整");
    }
    return new AmountCurrency(entry.getAmount().abs(), entry.getCurrency().trim().toUpperCase());
  }

  private Set<String> resolveCategoryAncestors(String categoryId) {
    Set<String> ancestors = new LinkedHashSet<>();
    if (categoryId == null || categoryId.isBlank()) return ancestors;

    Set<String> visited = new LinkedHashSet<>();
    String currentId = categoryId.trim();
    while (currentId != null && !currentId.isBlank()) {
      if (!visited.add(currentId)) {
        throw new IllegalStateException("分类层级存在循环，无法生成预算事实");
      }
      Category category = categoryMapper.selectById(currentId);
      if (category == null) {
        throw new IllegalStateException("预算事实引用的分类不存在：" + currentId);
      }
      String parentId = category.getParentId();
      if (parentId == null || parentId.isBlank()) break;
      parentId = parentId.trim();
      ancestors.add(parentId);
      currentId = parentId;
    }
    return ancestors;
  }

  private record AmountCurrency(BigDecimal amount, String currency) {}
}
