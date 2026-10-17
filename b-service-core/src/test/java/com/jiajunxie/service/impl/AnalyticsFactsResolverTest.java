package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jiajunxie.domain.analytics.AnalyticsFacts;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionAllocation;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.entity.LoanPaymentDetail;
import com.jiajunxie.mapper.TransactionAllocationMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.mapper.LoanPaymentDetailMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AnalyticsFactsResolverTest {

  @Test
  void incomeUsesSingleEntryAndItsOwnAnalysisDimensions() {
    Fixture f = fixture();

    Transaction income =
        transaction("income", "INCOME", "salary", null, "Employer");
    when(f.transactionMapper.selectById("income")).thenReturn(income);
    when(f.entryMapper.selectByTransactionId("income"))
        .thenReturn(List.of(entry("bank", "300000", "jpy")));
    when(f.allocationMapper.selectByTransactionId("income"))
        .thenReturn(List.of(allocation("person-a", "300000", "JPY")));

    AnalyticsFacts facts = f.resolver.resolve("income");

    assertEquals(new BigDecimal("300000"), facts.eligibleAmount());
    assertEquals("JPY", facts.currency());
    assertEquals("salary", facts.categoryId());
    assertEquals("Employer", facts.merchant());
    assertEquals(Set.of("bank"), facts.accountIds());
    assertEquals(
        new BigDecimal("300000"),
        facts.personAllocatedAmounts().get("person-a"));
  }

  @Test
  void creditRefundUsesOriginalCategoryAndMerchantButCurrentAccountAndAllocation() {
    Fixture f = fixture();

    Transaction refund =
        transaction("refund", "CREDIT_REFUND", "refund-category", "purchase", "Refund Merchant");
    refund.setOriginCode("CREDIT_REFUND");
    Transaction purchase =
        transaction("purchase", "CREDIT_PURCHASE", "food", null, "Cafe");

    when(f.transactionMapper.selectById("refund")).thenReturn(refund);
    when(f.transactionMapper.selectById("purchase")).thenReturn(purchase);
    when(f.entryMapper.selectByTransactionId("refund"))
        .thenReturn(List.of(entry("card", "-30", "JPY")));
    when(f.allocationMapper.selectByTransactionId("refund"))
        .thenReturn(List.of(allocation("person-a", "18", "JPY"), allocation("person-b", "12", "JPY")));

    AnalyticsFacts facts = f.resolver.resolve("refund");

    assertEquals("food", facts.categoryId());
    assertEquals("Cafe", facts.merchant());
    assertEquals(Set.of("card"), facts.accountIds());
    assertEquals(
        Map.of(
            "person-a", new BigDecimal("18"),
            "person-b", new BigDecimal("12")),
        facts.personAllocatedAmounts());
  }

  @Test
  void excludedTransferCanHaveMultipleEntriesAndNoMetricCurrency() {
    Fixture f = fixture();

    Transaction transfer =
        transaction("transfer", "TRANSFER", null, null, null);
    when(f.transactionMapper.selectById("transfer")).thenReturn(transfer);
    when(f.entryMapper.selectByTransactionId("transfer"))
        .thenReturn(
            List.of(
                entry("bank-a", "-100", "JPY"),
                entry("bank-b", "100", "JPY")));
    when(f.allocationMapper.selectByTransactionId("transfer")).thenReturn(List.of());

    AnalyticsFacts facts = f.resolver.resolve("transfer");

    assertEquals(BigDecimal.ZERO, facts.eligibleAmount());
    assertEquals(null, facts.currency());
    assertEquals(Set.of("bank-a", "bank-b"), facts.accountIds());
  }

  @Test
  void loanPaymentCountsOnlyInterestAndFeeAndUsesSourceAccount() {
    Fixture f = fixture();

    Transaction payment =
        transaction("loan-payment", "LOAN_PAYMENT", null, "loan-account", null);
    payment.setOriginCode("LOAN_ENGINE");

    when(f.transactionMapper.selectById("loan-payment")).thenReturn(payment);
    when(f.entryMapper.selectByTransactionId("loan-payment"))
        .thenReturn(
            List.of(
                entryWithRole("bank", "-1035", "JPY", "SOURCE"),
                entryWithRole("loan-account", "-1000", "JPY", "TARGET")));
    when(f.allocationMapper.selectByTransactionId("loan-payment")).thenReturn(List.of());

    LoanPaymentDetail detail = new LoanPaymentDetail();
    detail.setTransactionId("loan-payment");
    detail.setLoanAccountId("loan-account");
    detail.setPrincipalAmount(new BigDecimal("1000"));
    detail.setInterestAmount(new BigDecimal("30"));
    detail.setFeeAmount(new BigDecimal("5"));
    detail.setCurrency("JPY");
    when(f.loanPaymentDetailMapper.selectById("loan-payment")).thenReturn(detail);

    AnalyticsFacts facts = f.resolver.resolve("loan-payment");

    assertEquals(new BigDecimal("35"), facts.eligibleAmount());
    assertEquals("JPY", facts.currency());
    assertEquals(Set.of("bank"), facts.accountIds());
    assertEquals(null, facts.categoryId());
    assertEquals(null, facts.merchant());
    assertEquals(Map.of(), facts.personAllocatedAmounts());
  }

  @Test
  void loanPaymentWithoutDetailFailsClosed() {
    Fixture f = fixture();

    Transaction payment =
        transaction("loan-payment", "LOAN_PAYMENT", null, "loan-account", null);
    payment.setOriginCode("LOAN_ENGINE");

    when(f.transactionMapper.selectById("loan-payment")).thenReturn(payment);
    when(f.entryMapper.selectByTransactionId("loan-payment"))
        .thenReturn(List.of(entryWithRole("bank", "-100", "JPY", "SOURCE")));
    when(f.loanPaymentDetailMapper.selectById("loan-payment")).thenReturn(null);

    assertThrows(IllegalStateException.class, () -> f.resolver.resolve("loan-payment"));
  }

  @Test
  void statementFeeUsesSingleCardEntryWithoutInventingCategoryOrPerson() {
    Fixture f = fixture();

    Transaction fee =
        transaction("fee", "CREDIT_STATEMENT_FEE", null, "adjustment", null);
    fee.setOriginCode("STATEMENT_ADJUSTMENT");
    when(f.transactionMapper.selectById("fee")).thenReturn(fee);
    when(f.entryMapper.selectByTransactionId("fee"))
        .thenReturn(List.of(entry("card", "120", "JPY")));
    when(f.allocationMapper.selectByTransactionId("fee")).thenReturn(List.of());

    AnalyticsFacts facts = f.resolver.resolve("fee");

    assertEquals(new BigDecimal("120"), facts.eligibleAmount());
    assertEquals("JPY", facts.currency());
    assertEquals(null, facts.categoryId());
    assertEquals(null, facts.merchant());
    assertEquals(Map.of(), facts.personAllocatedAmounts());
  }

  @Test
  void allocationCurrencyMustMatchTransactionCurrency() {
    Fixture f = fixture();

    Transaction expense =
        transaction("expense", "EXPENSE", "food", null, "Cafe");
    when(f.transactionMapper.selectById("expense")).thenReturn(expense);
    when(f.entryMapper.selectByTransactionId("expense"))
        .thenReturn(List.of(entry("cash", "-100", "JPY")));
    when(f.allocationMapper.selectByTransactionId("expense"))
        .thenReturn(List.of(allocation("person-a", "100", "USD")));

    assertThrows(IllegalArgumentException.class, () -> f.resolver.resolve("expense"));
  }

  @Test
  void orphanCreditRefundFailsClosed() {
    Fixture f = fixture();

    Transaction refund =
        transaction("refund", "CREDIT_REFUND", "food", "missing-purchase", "Cafe");
    refund.setOriginCode("CREDIT_REFUND");
    when(f.transactionMapper.selectById("refund")).thenReturn(refund);
    when(f.transactionMapper.selectById("missing-purchase")).thenReturn(null);
    when(f.entryMapper.selectByTransactionId("refund"))
        .thenReturn(List.of(entry("card", "-30", "JPY")));

    assertThrows(IllegalStateException.class, () -> f.resolver.resolve("refund"));
  }

  private Fixture fixture() {
    TransactionMapper transactionMapper = mock(TransactionMapper.class);
    TransactionEntryMapper entryMapper = mock(TransactionEntryMapper.class);
    TransactionAllocationMapper allocationMapper = mock(TransactionAllocationMapper.class);
    LoanPaymentDetailMapper loanPaymentDetailMapper = mock(LoanPaymentDetailMapper.class);
    return new Fixture(
        new AnalyticsFactsResolver(
            transactionMapper,
            entryMapper,
            allocationMapper,
            loanPaymentDetailMapper),
        transactionMapper,
        entryMapper,
        allocationMapper,
        loanPaymentDetailMapper);
  }

  private Transaction transaction(
      String id,
      String type,
      String categoryId,
      String originReferenceId,
      String merchant) {
    Transaction transaction = new Transaction();
    transaction.setTransactionId(id);
    transaction.setTransactionType(type);
    transaction.setCategoryId(categoryId);
    transaction.setOriginReferenceId(originReferenceId);
    transaction.setMerchant(merchant);
    transaction.setTransactionTime(Instant.parse("2026-08-12T00:00:00Z"));
    return transaction;
  }

  private TransactionEntry entry(String accountId, String amount, String currency) {
    TransactionEntry entry = new TransactionEntry();
    entry.setAccountId(accountId);
    entry.setAmount(new BigDecimal(amount));
    entry.setCurrency(currency);
    return entry;
  }

  private TransactionEntry entryWithRole(
      String accountId,
      String amount,
      String currency,
      String role) {
    TransactionEntry entry = entry(accountId, amount, currency);
    entry.setEntryRole(role);
    return entry;
  }

  private TransactionAllocation allocation(
      String personId,
      String amount,
      String currency) {
    TransactionAllocation allocation = new TransactionAllocation();
    allocation.setPersonId(personId);
    allocation.setAllocatedAmount(new BigDecimal(amount));
    allocation.setCurrency(currency);
    return allocation;
  }

  private record Fixture(
      AnalyticsFactsResolver resolver,
      TransactionMapper transactionMapper,
      TransactionEntryMapper entryMapper,
      TransactionAllocationMapper allocationMapper,
      LoanPaymentDetailMapper loanPaymentDetailMapper) {}
}
