package com.jiajunxie.domain.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AnalyticsPeriodAggregationContractTest {
  private final AnalyticsPeriodAggregationContract contract =
      new AnalyticsPeriodAggregationContract();

  @Test
  void periodTotalsCountIncomeExpenseAndRefundExactlyOnce() {
    var result =
        contract.aggregate(
            List.of(
                facts("income", "INCOME", "300", "JPY", "salary", "Employer", "bank", Map.of()),
                facts("expense", "EXPENSE", "100", "JPY", "food", "Cafe", "cash", Map.of()),
                facts("refund", "REFUND", "30", "JPY", "food", "Cafe", "cash", Map.of())));

    var jpy = result.get("JPY");
    assertEquals(new BigDecimal("300"), jpy.totals().income());
    assertEquals(new BigDecimal("70"), jpy.totals().expense());
    assertEquals(new BigDecimal("230"), jpy.totals().netSurplus());
    assertEquals(new BigDecimal("70"), jpy.byCategory().get("food").expense());
    assertEquals(new BigDecimal("70"), jpy.byMerchant().get("Cafe").expense());
    assertEquals(new BigDecimal("70"), jpy.byAccount().get("cash").expense());
  }

  @Test
  void personBreakdownUsesAllocatedAmountsAndKeepsUnallocatedRemainder() {
    var result =
        contract.aggregate(
            List.of(
                facts(
                    "expense",
                    "EXPENSE",
                    "100",
                    "JPY",
                    "food",
                    "Cafe",
                    "cash",
                    Map.of(
                        "person-a", new BigDecimal("60"),
                        "person-b", new BigDecimal("30")))));

    var byPerson = result.get("JPY").byPerson();
    assertEquals(new BigDecimal("60"), byPerson.get("person-a").expense());
    assertEquals(new BigDecimal("30"), byPerson.get("person-b").expense());
    assertEquals(
        new BigDecimal("10"),
        byPerson.get(AnalyticsPeriodAggregationContract.UNALLOCATED_PERSON).expense());
  }

  @Test
  void transactionWithoutAllocationGoesEntirelyToUnallocatedPerson() {
    var result =
        contract.aggregate(
            List.of(
                facts(
                    "loan-purchase",
                    "LOAN_PURCHASE",
                    "200",
                    "JPY",
                    "shopping",
                    "Store",
                    "loan",
                    Map.of())));

    assertEquals(
        new BigDecimal("200"),
        result
            .get("JPY")
            .byPerson()
            .get(AnalyticsPeriodAggregationContract.UNALLOCATED_PERSON)
            .expense());
  }

  @Test
  void missingCategoryAndMerchantUseExplicitBuckets() {
    AnalyticsFacts fee =
        new AnalyticsFacts(
            "fee",
            "CREDIT_STATEMENT_FEE",
            "STATEMENT_ADJUSTMENT",
            "adjustment",
            Instant.parse("2026-08-12T00:00:00Z"),
            new BigDecimal("50"),
            "JPY",
            null,
            null,
            Set.of("card"),
            Map.of());

    var jpy = contract.aggregate(List.of(fee)).get("JPY");

    assertEquals(
        new BigDecimal("50"),
        jpy.byCategory().get(AnalyticsPeriodAggregationContract.UNCLASSIFIED).expense());
    assertEquals(
        new BigDecimal("50"),
        jpy.byMerchant().get(AnalyticsPeriodAggregationContract.UNSPECIFIED_MERCHANT).expense());
  }

  @Test
  void multipleCurrenciesStayInSeparateAggregations() {
    var result =
        contract.aggregate(
            List.of(
                facts("jpy", "EXPENSE", "100", "JPY", "food", "Cafe", "cash-jpy", Map.of()),
                facts("usd", "EXPENSE", "20", "USD", "food", "Cafe", "cash-usd", Map.of())));

    assertEquals(Set.of("JPY", "USD"), result.keySet());
    assertEquals(new BigDecimal("100"), result.get("JPY").totals().expense());
    assertEquals(new BigDecimal("20"), result.get("USD").totals().expense());
  }

  @Test
  void duplicateFactsAreDeduplicatedByTransactionId() {
    AnalyticsFacts facts =
        facts("tx", "EXPENSE", "100", "JPY", "food", "Cafe", "cash", Map.of());

    var result = contract.aggregate(List.of(facts, facts));

    assertEquals(new BigDecimal("100"), result.get("JPY").totals().expense());
  }

  @Test
  void zeroFinanceCostLoanPaymentDoesNotCreateNoiseBuckets() {
    AnalyticsFacts principalOnlyPayment =
        new AnalyticsFacts(
            "loan-payment",
            "LOAN_PAYMENT",
            "LOAN_ENGINE",
            "loan-account",
            Instant.parse("2026-08-12T00:00:00Z"),
            BigDecimal.ZERO,
            "JPY",
            null,
            null,
            Set.of("bank"),
            Map.of());

    assertEquals(Map.of(), contract.aggregate(List.of(principalOnlyPayment)));
  }

  @Test
  void excludedTransactionsDoNotCreateCurrencyBuckets() {
    AnalyticsFacts transfer =
        new AnalyticsFacts(
            "transfer",
            "TRANSFER",
            null,
            null,
            Instant.parse("2026-08-12T00:00:00Z"),
            BigDecimal.ZERO,
            null,
            null,
            null,
            Set.of("a", "b"),
            Map.of());

    assertEquals(Map.of(), contract.aggregate(List.of(transfer)));
  }

  @Test
  void overAllocatedPersonAmountsFailClosed() {
    AnalyticsFacts facts =
        facts(
            "tx",
            "EXPENSE",
            "100",
            "JPY",
            "food",
            "Cafe",
            "cash",
            Map.of("person-a", new BigDecimal("120")));

    assertThrows(
        IllegalStateException.class,
        () -> contract.aggregate(List.of(facts)));
  }

  private AnalyticsFacts facts(
      String id,
      String type,
      String amount,
      String currency,
      String categoryId,
      String merchant,
      String accountId,
      Map<String, BigDecimal> persons) {
    return new AnalyticsFacts(
        id,
        type,
        null,
        null,
        Instant.parse("2026-08-12T00:00:00Z"),
        new BigDecimal(amount),
        currency,
        categoryId,
        merchant,
        Set.of(accountId),
        persons);
  }
}
