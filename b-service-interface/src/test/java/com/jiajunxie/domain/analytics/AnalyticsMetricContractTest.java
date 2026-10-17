package com.jiajunxie.domain.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AnalyticsMetricContractTest {
  private final AnalyticsMetricContract contract = new AnalyticsMetricContract();

  @Test
  void incomeOnlyCountsRealIncomeTransactions() {
    assertEquals(
        new BigDecimal("100"),
        contract.incomeAmount("INCOME", null, new BigDecimal("100")));
    assertEquals(
        BigDecimal.ZERO,
        contract.incomeAmount("TRANSFER", null, new BigDecimal("100")));
    assertEquals(
        BigDecimal.ZERO,
        contract.incomeAmount("LOAN_DISBURSEMENT", null, new BigDecimal("100")));
  }

  @Test
  void expenseCountsCashCreditAndLoanPurchases() {
    assertEquals(
        new BigDecimal("100"),
        contract.expenseAmount("EXPENSE", null, new BigDecimal("-100")));
    assertEquals(
        new BigDecimal("100"),
        contract.expenseAmount("CREDIT_PURCHASE", null, new BigDecimal("100")));
    assertEquals(
        new BigDecimal("100"),
        contract.expenseAmount("LOAN_PURCHASE", "LOAN_PURCHASE", new BigDecimal("100")));
  }

  @Test
  void refundsReduceExpenseInsteadOfBecomingIncome() {
    assertEquals(
        new BigDecimal("-30"),
        contract.expenseAmount("REFUND", null, new BigDecimal("30")));
    assertEquals(
        new BigDecimal("-30"),
        contract.expenseAmount("CREDIT_REFUND", "CREDIT_REFUND", new BigDecimal("30")));
    assertEquals(
        BigDecimal.ZERO,
        contract.incomeAmount("CREDIT_REFUND", "CREDIT_REFUND", new BigDecimal("30")));
  }

  @Test
  void paymentsTransfersAndExchangeAreExcludedFromIncomeExpense() {
    for (String type :
        new String[] {
          "TRANSFER", "EXCHANGE", "CREDIT_PAYMENT", "LOAN_DISBURSEMENT"
        }) {
      assertEquals(BigDecimal.ZERO, contract.incomeAmount(type, null, new BigDecimal("100")));
      assertEquals(BigDecimal.ZERO, contract.expenseAmount(type, null, new BigDecimal("100")));
    }
  }

  @Test
  void loanPaymentFinanceCostIsExpenseWhenResolverSuppliesInterestAndFeeOnly() {
    assertEquals(
        new BigDecimal("35"),
        contract.expenseAmount("LOAN_PAYMENT", "LOAN_ENGINE", new BigDecimal("35")));
    assertEquals(
        BigDecimal.ZERO,
        contract.incomeAmount("LOAN_PAYMENT", "LOAN_ENGINE", new BigDecimal("35")));
  }

  @Test
  void statementFeeAndInterestCountAsExpenseAndReversalReducesExpense() {
    assertEquals(
        new BigDecimal("100"),
        contract.expenseAmount(
            "CREDIT_STATEMENT_FEE", "STATEMENT_ADJUSTMENT", new BigDecimal("100")));
    assertEquals(
        new BigDecimal("80"),
        contract.expenseAmount(
            "CREDIT_STATEMENT_INTEREST", "STATEMENT_ADJUSTMENT", new BigDecimal("80")));
    assertEquals(
        new BigDecimal("-100"),
        contract.expenseAmount(
            "CREDIT_STATEMENT_FEE",
            "STATEMENT_ADJUSTMENT_REVERSAL",
            new BigDecimal("100")));
  }

  @Test
  void unknownStatementAdjustmentFailsClosed() {
    assertThrows(
        IllegalStateException.class,
        () ->
            contract.expenseAmount(
                "EXPENSE", "STATEMENT_ADJUSTMENT", new BigDecimal("100")));
  }

  @Test
  void netSurplusUsesIncomeMinusNetExpense() {
    assertEquals(
        new BigDecimal("100"),
        contract.netSurplus("INCOME", null, new BigDecimal("100")));
    assertEquals(
        new BigDecimal("-100"),
        contract.netSurplus("EXPENSE", null, new BigDecimal("100")));
    assertEquals(
        new BigDecimal("30"),
        contract.netSurplus("REFUND", null, new BigDecimal("30")));
  }

  @Test
  void crossCurrencyAggregationIsRejected() {
    contract.assertComparableCurrency("JPY", "JPY");
    assertThrows(
        IllegalArgumentException.class,
        () -> contract.assertComparableCurrency("JPY", "USD"));
  }
}
