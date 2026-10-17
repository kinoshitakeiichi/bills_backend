package com.jiajunxie.domain.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jiajunxie.domain.budget.BudgetTargetMatcherContract.Facts;
import com.jiajunxie.domain.budget.BudgetTargetMatcherContract.Target;
import com.jiajunxie.domain.budget.BudgetTargetMatcherContract.TargetType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BudgetItemActualAggregationContractTest {
  private final BudgetItemActualAggregationContract contract =
      new BudgetItemActualAggregationContract();

  @Test
  void sameTypeTargetsAreOrWithoutDoubleCountingTransaction() {
    BudgetActualFacts facts =
        facts(
            "tx",
            "EXPENSE",
            "100",
            "JPY",
            new Facts("food-child", Set.of("food-parent"), Set.of("cash"), "Cafe", Map.of()));

    List<Target> targets =
        List.of(
            new Target(TargetType.CATEGORY, "food-child", null, false),
            new Target(TargetType.CATEGORY, "food-parent", null, true));

    assertEquals(new BigDecimal("100"), contract.aggregate("JPY", targets, List.of(facts)));
  }

  @Test
  void differentTargetTypesAreAnd() {
    BudgetActualFacts facts =
        facts(
            "tx",
            "EXPENSE",
            "100",
            "JPY",
            new Facts("food", Set.of(), Set.of("cash"), "Cafe", Map.of()));

    assertEquals(
        new BigDecimal("100"),
        contract.aggregate(
            "JPY",
            List.of(
                new Target(TargetType.CATEGORY, "food", null, false),
                new Target(TargetType.ACCOUNT, "cash", null, false)),
            List.of(facts)));

    assertEquals(
        BigDecimal.ZERO,
        contract.aggregate(
            "JPY",
            List.of(
                new Target(TargetType.CATEGORY, "food", null, false),
                new Target(TargetType.ACCOUNT, "card", null, false)),
            List.of(facts)));
  }

  @Test
  void multiplePersonTargetsSumDistinctAllocatedAmountsOnly() {
    BudgetActualFacts facts =
        facts(
            "tx",
            "EXPENSE",
            "100",
            "JPY",
            new Facts(
                "food",
                Set.of(),
                Set.of("cash"),
                "Cafe",
                Map.of(
                    "person-a", new BigDecimal("60"),
                    "person-b", new BigDecimal("40"))));

    List<Target> targets =
        List.of(
            new Target(TargetType.PERSON, "person-a", null, false),
            new Target(TargetType.PERSON, "person-a", null, false),
            new Target(TargetType.PERSON, "person-b", null, false));

    assertEquals(new BigDecimal("100"), contract.aggregate("JPY", targets, List.of(facts)));
  }

  @Test
  void categoryAndPersonFiltersReturnOnlySelectedPersonAmount() {
    BudgetActualFacts facts =
        facts(
            "tx",
            "CREDIT_PURCHASE",
            "100",
            "JPY",
            new Facts(
                "food",
                Set.of(),
                Set.of("card"),
                "Cafe",
                Map.of(
                    "person-a", new BigDecimal("60"),
                    "person-b", new BigDecimal("40"))));

    assertEquals(
        new BigDecimal("60"),
        contract.aggregate(
            "JPY",
            List.of(
                new Target(TargetType.CATEGORY, "food", null, false),
                new Target(TargetType.PERSON, "person-a", null, false)),
            List.of(facts)));
  }

  @Test
  void refundUsesNegativeSignAfterTargetMatching() {
    BudgetActualFacts refund =
        facts(
            "refund",
            "CREDIT_REFUND",
            "30",
            "JPY",
            new Facts(
                "food",
                Set.of(),
                Set.of("card"),
                "Cafe",
                Map.of(
                    "person-a", new BigDecimal("18"),
                    "person-b", new BigDecimal("12"))));

    assertEquals(
        new BigDecimal("-18"),
        contract.aggregate(
            "JPY",
            List.of(new Target(TargetType.PERSON, "person-a", null, false)),
            List.of(refund)));
  }

  @Test
  void duplicateFactRowsAreAggregatedOnceByTransactionId() {
    BudgetActualFacts facts =
        facts(
            "tx",
            "EXPENSE",
            "100",
            "JPY",
            new Facts("food", Set.of(), Set.of("cash"), "Cafe", Map.of()));

    assertEquals(
        new BigDecimal("100"),
        contract.aggregate("JPY", List.of(), List.of(facts, facts)));
  }

  @Test
  void excludedTransactionsDoNotTriggerCurrencyComparison() {
    BudgetActualFacts transfer =
        facts(
            "transfer",
            "TRANSFER",
            "0",
            null,
            new Facts(null, Set.of(), Set.of("a", "b"), null, Map.of()));

    assertEquals(BigDecimal.ZERO, contract.aggregate("JPY", List.of(), List.of(transfer)));
  }

  @Test
  void crossCurrencyMatchedActualFailsClosed() {
    BudgetActualFacts expense =
        facts(
            "tx",
            "EXPENSE",
            "100",
            "USD",
            new Facts("food", Set.of(), Set.of("cash"), "Cafe", Map.of()));

    assertThrows(
        IllegalArgumentException.class,
        () -> contract.aggregate("JPY", List.of(), List.of(expense)));
  }

  @Test
  void loanPurchaseIsARegularExpenseActual() {
    BudgetActualFacts loanPurchase =
        facts(
            "loan",
            "LOAN_PURCHASE",
            "100",
            "JPY",
            new Facts("shopping", Set.of(), Set.of("loan"), "Store", Map.of()));

    assertEquals(
        new BigDecimal("100"),
        contract.aggregate("JPY", List.of(), List.of(loanPurchase)));
  }

  @Test
  void statementFeeAndReversalNetCorrectly() {
    BudgetActualFacts fee =
        new BudgetActualFacts(
            "fee",
            "CREDIT_STATEMENT_FEE",
            "STATEMENT_ADJUSTMENT",
            "adjustment",
            Instant.parse("2026-08-12T00:00:00Z"),
            new BigDecimal("100"),
            "JPY",
            new Facts(null, Set.of(), Set.of("card"), null, Map.of()));
    BudgetActualFacts reversal =
        new BudgetActualFacts(
            "reversal",
            "CREDIT_STATEMENT_FEE",
            "STATEMENT_ADJUSTMENT_REVERSAL",
            "adjustment",
            Instant.parse("2026-08-12T01:00:00Z"),
            new BigDecimal("100"),
            "JPY",
            new Facts(null, Set.of(), Set.of("card"), null, Map.of()));

    assertEquals(
        BigDecimal.ZERO,
        contract.aggregate("JPY", List.of(), List.of(fee, reversal)));
  }

  @Test
  void unknownStatementAdjustmentStillFailsClosed() {
    BudgetActualFacts adjustment =
        new BudgetActualFacts(
            "adjustment",
            "EXPENSE",
            "STATEMENT_ADJUSTMENT",
            null,
            Instant.parse("2026-08-12T00:00:00Z"),
            new BigDecimal("100"),
            "JPY",
            new Facts("fee", Set.of(), Set.of("card"), "Bank", Map.of()));

    assertThrows(
        IllegalStateException.class,
        () -> contract.aggregate("JPY", List.of(), List.of(adjustment)));
  }

  private BudgetActualFacts facts(
      String id,
      String type,
      String amount,
      String currency,
      Facts targetFacts) {
    return new BudgetActualFacts(
        id,
        type,
        null,
        null,
        Instant.parse("2026-08-12T00:00:00Z"),
        new BigDecimal(amount),
        currency,
        targetFacts);
  }
}
