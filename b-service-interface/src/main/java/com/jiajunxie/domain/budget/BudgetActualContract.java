package com.jiajunxie.domain.budget;

import java.math.BigDecimal;
import java.util.Locale;

public final class BudgetActualContract {
  public enum ActualEffect {
    INCOME,
    EXPENSE,
    REFUND,
    EXCLUDED,
    UNRESOLVED
  }

  public ActualEffect classify(String transactionType, String originCode) {
    String type = normalize(transactionType);
    String origin = normalize(originCode);

    if ("STATEMENT_ADJUSTMENT".equals(origin)) {
      return isStatementFinanceCost(type) ? ActualEffect.EXPENSE : ActualEffect.UNRESOLVED;
    }
    if ("STATEMENT_ADJUSTMENT_REVERSAL".equals(origin)) {
      return isStatementFinanceCost(type) ? ActualEffect.REFUND : ActualEffect.UNRESOLVED;
    }

    return switch (type) {
      case "INCOME" -> ActualEffect.INCOME;
      case "EXPENSE", "CREDIT_PURCHASE", "LOAN_PURCHASE" -> ActualEffect.EXPENSE;
      case "REFUND", "CREDIT_REFUND" -> ActualEffect.REFUND;
      case "TRANSFER", "EXCHANGE", "CREDIT_PAYMENT", "LOAN_DISBURSEMENT", "LOAN_PAYMENT" ->
          ActualEffect.EXCLUDED;
      default -> ActualEffect.EXCLUDED;
    };
  }

  public BigDecimal signedActual(
      String transactionType, String originCode, BigDecimal eligibleAmount) {
    if (eligibleAmount == null) return BigDecimal.ZERO;
    BigDecimal amount = eligibleAmount.abs();
    return switch (classify(transactionType, originCode)) {
      case INCOME, EXPENSE -> amount;
      case REFUND -> amount.negate();
      case EXCLUDED -> BigDecimal.ZERO;
      case UNRESOLVED ->
          throw new IllegalStateException(
              "预算语义尚未冻结，禁止自动纳入 actual：" + normalize(transactionType));
    };
  }

  public void assertComparableCurrency(String budgetItemCurrency, String actualCurrency) {
    if (!normalize(budgetItemCurrency).equals(normalize(actualCurrency))) {
      throw new IllegalArgumentException("预算 actual 第一版禁止跨币种直接比较或汇总");
    }
  }


  private boolean isStatementFinanceCost(String transactionType) {
    return "CREDIT_STATEMENT_FEE".equals(transactionType)
        || "CREDIT_STATEMENT_INTEREST".equals(transactionType);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
