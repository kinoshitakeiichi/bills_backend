package com.jiajunxie.service.impl;

import com.jiajunxie.domain.analytics.AnalyticsFacts;
import com.jiajunxie.domain.analytics.AnalyticsMetricContract;
import com.jiajunxie.domain.budget.BudgetDimensionSourceContract;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionAllocation;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.entity.LoanPaymentDetail;
import com.jiajunxie.mapper.TransactionAllocationMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.mapper.LoanPaymentDetailMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnalyticsFactsResolver {
  private static final AnalyticsMetricContract METRIC_CONTRACT = new AnalyticsMetricContract();
  private static final BudgetDimensionSourceContract DIMENSION_SOURCE =
      new BudgetDimensionSourceContract();

  private final TransactionMapper transactionMapper;
  private final TransactionEntryMapper entryMapper;
  private final TransactionAllocationMapper allocationMapper;
  private final LoanPaymentDetailMapper loanPaymentDetailMapper;

  public AnalyticsFacts resolve(String transactionId) {
    if (transactionId == null || transactionId.isBlank()) {
      throw new IllegalArgumentException("transactionId is required");
    }

    Transaction transaction = requireTransaction(transactionId.trim());
    AnalyticsMetricContract.MetricEffect effect =
        METRIC_CONTRACT.classify(transaction.getTransactionType(), transaction.getOriginCode());

    if (effect == AnalyticsMetricContract.MetricEffect.UNRESOLVED) {
      METRIC_CONTRACT.expenseAmount(
          transaction.getTransactionType(), transaction.getOriginCode(), BigDecimal.ONE);
    }

    List<TransactionEntry> currentEntries =
        entryMapper.selectByTransactionId(transaction.getTransactionId());
    AmountCurrency amountCurrency =
        "LOAN_PAYMENT".equals(normalizeCode(transaction.getTransactionType()))
            ? resolveLoanPaymentAmountCurrency(transaction.getTransactionId())
            : resolveAmountCurrency(effect, currentEntries);

    Transaction dimensionSource = resolveDimensionSource(transaction);
    Set<String> currentAccountIds =
        "LOAN_PAYMENT".equals(normalizeCode(transaction.getTransactionType()))
            ? resolveLoanPaymentSourceAccountIds(currentEntries)
            : resolveAccountIds(currentEntries);
    Map<String, BigDecimal> currentPersonAmounts =
        resolvePersonAmounts(transaction.getTransactionId(), amountCurrency.currency());

    return new AnalyticsFacts(
        transaction.getTransactionId(),
        transaction.getTransactionType(),
        transaction.getOriginCode(),
        transaction.getOriginReferenceId(),
        transaction.getTransactionTime(),
        amountCurrency.amount(),
        amountCurrency.currency(),
        dimensionSource.getCategoryId(),
        dimensionSource.getMerchant(),
        currentAccountIds,
        currentPersonAmounts);
  }

  private Transaction resolveDimensionSource(Transaction transaction) {
    String sourceId =
        DIMENSION_SOURCE.dimensionSourceTransactionId(
            transaction.getTransactionId(),
            transaction.getTransactionType(),
            transaction.getOriginReferenceId());
    return transaction.getTransactionId().equals(sourceId)
        ? transaction
        : requireTransaction(sourceId);
  }

  private Transaction requireTransaction(String transactionId) {
    Transaction transaction = transactionMapper.selectById(transactionId);
    if (transaction == null) {
      throw new IllegalStateException("Analytics 事实引用的交易不存在：" + transactionId);
    }
    return transaction;
  }

  private AmountCurrency resolveLoanPaymentAmountCurrency(String transactionId) {
    LoanPaymentDetail detail = loanPaymentDetailMapper.selectById(transactionId);
    if (detail == null) {
      throw new IllegalStateException("Analytics 贷款还款明细不存在：" + transactionId);
    }
    if (detail.getInterestAmount() == null
        || detail.getFeeAmount() == null
        || detail.getCurrency() == null
        || detail.getCurrency().isBlank()) {
      throw new IllegalStateException("Analytics 贷款还款利息/手续费事实不完整：" + transactionId);
    }

    BigDecimal financeCost =
        detail.getInterestAmount().abs().add(detail.getFeeAmount().abs());
    return new AmountCurrency(financeCost, normalizeCurrency(detail.getCurrency()));
  }

  private Set<String> resolveLoanPaymentSourceAccountIds(List<TransactionEntry> entries) {
    Set<String> sourceAccountIds = new LinkedHashSet<>();
    if (entries == null) return sourceAccountIds;

    for (TransactionEntry entry : entries) {
      if (!"SOURCE".equals(normalizeCode(entry.getEntryRole()))) continue;
      if (entry.getAccountId() != null && !entry.getAccountId().isBlank()) {
        sourceAccountIds.add(entry.getAccountId().trim());
      }
    }

    if (sourceAccountIds.size() != 1) {
      throw new IllegalStateException("Analytics 贷款还款必须且只能有一个扣款来源账户");
    }
    return sourceAccountIds;
  }

  private AmountCurrency resolveAmountCurrency(
      AnalyticsMetricContract.MetricEffect effect,
      List<TransactionEntry> entries) {
    if (effect == AnalyticsMetricContract.MetricEffect.EXCLUDED) {
      return new AmountCurrency(BigDecimal.ZERO, null);
    }
    if (entries == null || entries.size() != 1) {
      throw new IllegalStateException("Analytics 可统计交易必须且只能有一条余额分录");
    }

    TransactionEntry entry = entries.get(0);
    if (entry.getAmount() == null
        || entry.getCurrency() == null
        || entry.getCurrency().isBlank()) {
      throw new IllegalStateException("Analytics 可统计交易的金额或币种事实不完整");
    }
    return new AmountCurrency(
        entry.getAmount().abs(),
        normalizeCurrency(entry.getCurrency()));
  }

  private Set<String> resolveAccountIds(List<TransactionEntry> entries) {
    Set<String> accountIds = new LinkedHashSet<>();
    if (entries == null) return accountIds;
    for (TransactionEntry entry : entries) {
      if (entry.getAccountId() != null && !entry.getAccountId().isBlank()) {
        accountIds.add(entry.getAccountId().trim());
      }
    }
    return accountIds;
  }

  private Map<String, BigDecimal> resolvePersonAmounts(
      String transactionId,
      String transactionCurrency) {
    Map<String, BigDecimal> amounts = new LinkedHashMap<>();
    for (TransactionAllocation allocation : allocationMapper.selectByTransactionId(transactionId)) {
      if (allocation.getPersonId() == null || allocation.getPersonId().isBlank()) continue;
      if (allocation.getAllocatedAmount() == null) continue;

      if (transactionCurrency != null) {
        String allocationCurrency = normalizeCurrency(allocation.getCurrency());
        METRIC_CONTRACT.assertComparableCurrency(transactionCurrency, allocationCurrency);
      }

      amounts.merge(
          allocation.getPersonId().trim(),
          allocation.getAllocatedAmount().abs(),
          BigDecimal::add);
    }
    return amounts;
  }

  private String normalizeCode(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeCurrency(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private record AmountCurrency(BigDecimal amount, String currency) {}
}
