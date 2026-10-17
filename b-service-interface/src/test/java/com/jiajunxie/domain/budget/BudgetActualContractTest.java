package com.jiajunxie.domain.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BudgetActualContractTest {
  private final BudgetActualContract contract = new BudgetActualContract();

  @Test
  void expenseCreditPurchaseAndLoanPurchaseIncreaseActual() {
    assertEquals(
        new BigDecimal("100"),
        contract.signedActual("EXPENSE", null, new BigDecimal("-100")));
    assertEquals(
        new BigDecimal("100"),
        contract.signedActual("CREDIT_PURCHASE", null, new BigDecimal("100")));
    assertEquals(
        new BigDecimal("100"),
        contract.signedActual("LOAN_PURCHASE", "LOAN_PURCHASE", new BigDecimal("100")));
  }


  @Test
  void incomeIncreasesActual() {
    assertEquals(
        new BigDecimal("100"),
        contract.signedActual("INCOME", null, new BigDecimal("100")));
  }

  @Test
  void refundAndCreditRefundReduceActual() {
    assertEquals(
        new BigDecimal("-30"),
        contract.signedActual("REFUND", null, new BigDecimal("30")));
    assertEquals(
        new BigDecimal("-30"),
        contract.signedActual("CREDIT_REFUND", "CREDIT_REFUND", new BigDecimal("30")));
  }

  @Test
  void transferCreditPaymentExchangeAndLoanPaymentAreExcluded() {
    for (String type :
        new String[] {"TRANSFER", "CREDIT_PAYMENT", "EXCHANGE", "LOAN_DISBURSEMENT", "LOAN_PAYMENT"}) {
      assertEquals(
          BigDecimal.ZERO,
          contract.signedActual(type, null, new BigDecimal("100")));
    }
  }

  @Test
  void statementFeeAndInterestAreExpenseAndReversalReducesActual() {
    assertEquals(
        new BigDecimal("100"),
        contract.signedActual(
            "CREDIT_STATEMENT_FEE",
            "STATEMENT_ADJUSTMENT",
            new BigDecimal("100")));
    assertEquals(
        new BigDecimal("80"),
        contract.signedActual(
            "CREDIT_STATEMENT_INTEREST",
            "STATEMENT_ADJUSTMENT",
            new BigDecimal("80")));
    assertEquals(
        new BigDecimal("-100"),
        contract.signedActual(
            "CREDIT_STATEMENT_FEE",
            "STATEMENT_ADJUSTMENT_REVERSAL",
            new BigDecimal("100")));
  }

  @Test
  void unknownStatementAdjustmentStillFailsClosed() {
    assertThrows(
        IllegalStateException.class,
        () ->
            contract.signedActual(
                "EXPENSE",
                "STATEMENT_ADJUSTMENT",
                new BigDecimal("100")));
  }

  @Test
  void crossCurrencyComparisonIsRejected() {
    contract.assertComparableCurrency("JPY", "JPY");
    assertThrows(
        IllegalArgumentException.class,
        () -> contract.assertComparableCurrency("JPY", "USD"));
  }
}
