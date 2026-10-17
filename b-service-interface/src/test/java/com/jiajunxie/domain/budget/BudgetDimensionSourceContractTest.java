package com.jiajunxie.domain.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BudgetDimensionSourceContractTest {
  private final BudgetDimensionSourceContract contract = new BudgetDimensionSourceContract();

  @Test
  void linkedRefundAndCreditRefundUseOriginalTransactionDimensions() {
    assertEquals(
        "expense-origin",
        contract.dimensionSourceTransactionId("refund", "REFUND", "expense-origin"));
    assertEquals(
        "purchase-origin",
        contract.dimensionSourceTransactionId(
            "credit-refund", "CREDIT_REFUND", "purchase-origin"));
  }

  @Test
  void nonRefundUsesItsOwnDimensions() {
    assertEquals(
        "expense",
        contract.dimensionSourceTransactionId("expense", "EXPENSE", null));
  }

  @Test
  void ordinaryRefundWithoutOriginUsesItsOwnExplicitDimensions() {
    assertEquals(
        "refund",
        contract.dimensionSourceTransactionId("refund", "REFUND", null));
  }

  @Test
  void creditRefundWithoutOriginFailsClosed() {
    assertThrows(
        IllegalStateException.class,
        () -> contract.dimensionSourceTransactionId("credit-refund", "CREDIT_REFUND", null));
  }
}
