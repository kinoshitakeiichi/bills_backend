package com.jiajunxie.domain.analytics;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public final class AnalyticsPeriodAggregationContract {
  public static final String UNCLASSIFIED = "__UNCLASSIFIED__";
  public static final String UNSPECIFIED_MERCHANT = "__UNSPECIFIED_MERCHANT__";
  public static final String UNALLOCATED_PERSON = "__UNALLOCATED_PERSON__";

  private final AnalyticsMetricContract metricContract = new AnalyticsMetricContract();

  public record MetricTotals(
      BigDecimal income,
      BigDecimal expense,
      BigDecimal netSurplus) {}

  public record CurrencyAggregation(
      String currency,
      MetricTotals totals,
      Map<String, MetricTotals> byCategory,
      Map<String, MetricTotals> byAccount,
      Map<String, MetricTotals> byPerson,
      Map<String, MetricTotals> byMerchant) {

    public CurrencyAggregation {
      byCategory = Collections.unmodifiableMap(new LinkedHashMap<>(byCategory));
      byAccount = Collections.unmodifiableMap(new LinkedHashMap<>(byAccount));
      byPerson = Collections.unmodifiableMap(new LinkedHashMap<>(byPerson));
      byMerchant = Collections.unmodifiableMap(new LinkedHashMap<>(byMerchant));
    }
  }

  public Map<String, CurrencyAggregation> aggregate(List<AnalyticsFacts> factsList) {
    if (factsList == null || factsList.isEmpty()) return Map.of();

    Map<String, MutableCurrencyAggregation> currencies = new LinkedHashMap<>();
    Set<String> seenTransactionIds = new HashSet<>();

    for (AnalyticsFacts facts : factsList) {
      if (facts == null) continue;
      String transactionId = normalizeId(facts.transactionId());
      if (transactionId.isEmpty()) {
        throw new IllegalArgumentException("analytics transactionId is required");
      }
      if (!seenTransactionIds.add(transactionId)) continue;

      AnalyticsMetricContract.MetricEffect effect =
          metricContract.classify(facts.transactionType(), facts.originCode());
      if (effect == AnalyticsMetricContract.MetricEffect.EXCLUDED) continue;
      if (effect == AnalyticsMetricContract.MetricEffect.UNRESOLVED) {
        metricContract.expenseAmount(
            facts.transactionType(), facts.originCode(), facts.eligibleAmount());
      }

      String currency = normalizeCurrency(facts.currency());
      if (currency.isEmpty()) {
        throw new IllegalStateException("可统计交易缺少币种：" + facts.transactionId());
      }

      BigDecimal amount = abs(facts.eligibleAmount());
      MetricDelta transactionDelta = delta(facts, amount);
      if (transactionDelta.income().signum() == 0
          && transactionDelta.expense().signum() == 0) {
        continue;
      }

      MutableCurrencyAggregation aggregate =
          currencies.computeIfAbsent(currency, ignored -> new MutableCurrencyAggregation(currency));
      aggregate.totals.add(transactionDelta);

      aggregate.add(
          aggregate.byCategory,
          normalizeDimension(facts.categoryId(), UNCLASSIFIED),
          transactionDelta);

      String merchant =
          facts.merchant() == null || facts.merchant().isBlank()
              ? UNSPECIFIED_MERCHANT
              : facts.merchant().trim();
      aggregate.add(aggregate.byMerchant, merchant, transactionDelta);

      if (facts.accountIds().isEmpty()) {
        aggregate.add(aggregate.byAccount, UNCLASSIFIED, transactionDelta);
      } else {
        // Every currently eligible analytics event is a single-entry event. Keep this defensive
        // guard so a future multi-entry eligible event cannot silently multiply totals by account.
        if (facts.accountIds().size() != 1) {
          throw new IllegalStateException(
              "可统计交易存在多个参与账户，Account breakdown 语义尚未冻结："
                  + facts.transactionId());
        }
        aggregate.add(aggregate.byAccount, facts.accountIds().iterator().next(), transactionDelta);
      }

      addPersonBreakdown(aggregate, facts, amount);
    }

    Map<String, CurrencyAggregation> result = new LinkedHashMap<>();
    for (Map.Entry<String, MutableCurrencyAggregation> entry : currencies.entrySet()) {
      result.put(entry.getKey(), entry.getValue().freeze());
    }
    return Collections.unmodifiableMap(result);
  }

  private void addPersonBreakdown(
      MutableCurrencyAggregation aggregate,
      AnalyticsFacts facts,
      BigDecimal transactionAmount) {
    if (facts.personAllocatedAmounts().isEmpty()) {
      aggregate.add(
          aggregate.byPerson,
          UNALLOCATED_PERSON,
          delta(facts, transactionAmount));
      return;
    }

    BigDecimal allocatedTotal = BigDecimal.ZERO;
    for (Map.Entry<String, BigDecimal> entry : facts.personAllocatedAmounts().entrySet()) {
      String personId = normalizeId(entry.getKey());
      if (personId.isEmpty()) continue;
      BigDecimal allocated = abs(entry.getValue());
      allocatedTotal = allocatedTotal.add(allocated);
      aggregate.add(aggregate.byPerson, personId, delta(facts, allocated));
    }

    if (allocatedTotal.compareTo(transactionAmount) > 0) {
      throw new IllegalStateException(
          "人员分摊金额超过交易金额：" + facts.transactionId());
    }

    BigDecimal unallocated = transactionAmount.subtract(allocatedTotal);
    if (unallocated.signum() > 0) {
      aggregate.add(
          aggregate.byPerson,
          UNALLOCATED_PERSON,
          delta(facts, unallocated));
    }
  }

  private MetricDelta delta(AnalyticsFacts facts, BigDecimal amount) {
    return new MetricDelta(
        metricContract.incomeAmount(facts.transactionType(), facts.originCode(), amount),
        metricContract.expenseAmount(facts.transactionType(), facts.originCode(), amount));
  }

  private BigDecimal abs(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value.abs();
  }

  private String normalizeId(String value) {
    return value == null ? "" : value.trim();
  }

  private String normalizeCurrency(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeDimension(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private record MetricDelta(BigDecimal income, BigDecimal expense) {}

  private static final class MutableTotals {
    private BigDecimal income = BigDecimal.ZERO;
    private BigDecimal expense = BigDecimal.ZERO;

    void add(MetricDelta delta) {
      income = income.add(delta.income());
      expense = expense.add(delta.expense());
    }

    MetricTotals freeze() {
      return new MetricTotals(income, expense, income.subtract(expense));
    }
  }

  private static final class MutableCurrencyAggregation {
    private final String currency;
    private final MutableTotals totals = new MutableTotals();
    private final Map<String, MutableTotals> byCategory = new LinkedHashMap<>();
    private final Map<String, MutableTotals> byAccount = new LinkedHashMap<>();
    private final Map<String, MutableTotals> byPerson = new LinkedHashMap<>();
    private final Map<String, MutableTotals> byMerchant = new LinkedHashMap<>();

    private MutableCurrencyAggregation(String currency) {
      this.currency = currency;
    }

    private void add(
        Map<String, MutableTotals> target,
        String key,
        MetricDelta delta) {
      target.computeIfAbsent(key, ignored -> new MutableTotals()).add(delta);
    }

    private CurrencyAggregation freeze() {
      return new CurrencyAggregation(
          currency,
          totals.freeze(),
          freezeMap(byCategory),
          freezeMap(byAccount),
          freezeMap(byPerson),
          freezeMap(byMerchant));
    }

    private Map<String, MetricTotals> freezeMap(Map<String, MutableTotals> source) {
      Map<String, MetricTotals> result = new LinkedHashMap<>();
      for (Map.Entry<String, MutableTotals> entry : source.entrySet()) {
        result.put(entry.getKey(), entry.getValue().freeze());
      }
      return result;
    }
  }
}
