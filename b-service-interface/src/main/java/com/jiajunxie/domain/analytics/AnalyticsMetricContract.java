package com.jiajunxie.domain.analytics;

import java.math.BigDecimal;
import java.util.Locale;

public final class AnalyticsMetricContract {

  public enum MetricEffect {
    INCOME,
    EXPENSE,
    REFUND,
    EXCLUDED,
    UNRESOLVED
  }

  public MetricEffect classify(String transactionType, String originCode) {
    String type = normalize(transactionType);
    String origin = normalize(originCode);

    if ("STATEMENT_ADJUSTMENT".equals(origin)) {
      return isStatementFinanceCost(type) ? MetricEffect.EXPENSE : MetricEffect.UNRESOLVED;
    }
    if ("STATEMENT_ADJUSTMENT_REVERSAL".equals(origin)) {
      return isStatementFinanceCost(type) ? MetricEffect.REFUND : MetricEffect.UNRESOLVED;
    }

    return switch (type) {
      case "INCOME" -> MetricEffect.INCOME;
      case "EXPENSE", "CREDIT_PURCHASE", "LOAN_PURCHASE", "LOAN_PAYMENT" -> MetricEffect.EXPENSE;
      case "REFUND", "CREDIT_REFUND" -> MetricEffect.REFUND;
      case "TRANSFER",
          "EXCHANGE",
          "CREDIT_PAYMENT",
          "LOAN_DISBURSEMENT" -> MetricEffect.EXCLUDED;
      default -> MetricEffect.EXCLUDED;
    };
  }

  public BigDecimal incomeAmount(
      String transactionType, String originCode, BigDecimal eligibleAmount) {
    return switch (classify(transactionType, originCode)) {
      case INCOME -> abs(eligibleAmount);
      case EXPENSE, REFUND, EXCLUDED -> BigDecimal.ZERO;
      case UNRESOLVED -> throw unresolved(transactionType);
    };
  }

  /**
   * Net consumer outflow:
   * expense/finance cost = positive, refund/reversal = negative.
   */
  public BigDecimal expenseAmount(
      String transactionType, String originCode, BigDecimal eligibleAmount) {
    BigDecimal amount = abs(eligibleAmount);
    return switch (classify(transactionType, originCode)) {
      case EXPENSE -> amount;
      case REFUND -> amount.negate();
      case INCOME, EXCLUDED -> BigDecimal.ZERO;
      case UNRESOLVED -> throw unresolved(transactionType);
    };
  }

  /**
   * Accounting-style surplus = real income - net consumer expense.
   *
   * <p>This is intentionally NOT named cashflow: CREDIT_PURCHASE / LOAN_PURCHASE are expenses even
   * when cash does not move at purchase time.
   */
  public BigDecimal netSurplus(
      String transactionType, String originCode, BigDecimal eligibleAmount) {
    return incomeAmount(transactionType, originCode, eligibleAmount)
        .subtract(expenseAmount(transactionType, originCode, eligibleAmount));
  }

  public void assertComparableCurrency(String metricCurrency, String transactionCurrency) {
    if (!normalize(metricCurrency).equals(normalize(transactionCurrency))) {
      throw new IllegalArgumentException("Analytics 第一版禁止跨币种直接汇总");
    }
  }

  private boolean isStatementFinanceCost(String transactionType) {
    return "CREDIT_STATEMENT_FEE".equals(transactionType)
        || "CREDIT_STATEMENT_INTEREST".equals(transactionType);
  }

  private BigDecimal abs(BigDecimal amount) {
    return amount == null ? BigDecimal.ZERO : amount.abs();
  }

  private IllegalStateException unresolved(String transactionType) {
    return new IllegalStateException("Analytics 语义尚未冻结：" + normalize(transactionType));
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
