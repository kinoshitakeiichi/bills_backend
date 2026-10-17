package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiajunxie.domain.budget.BudgetActualFacts;
import com.jiajunxie.entity.Category;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionAllocation;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.mapper.TransactionAllocationMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BudgetActualFactsResolverTest {

  @Test
  void creditRefundUsesOriginalCategoryAndMerchantButCurrentAccountsAndPersonAmounts() {
    Fixture f = fixture();

    Transaction refund =
        transaction(
            "refund",
            "CREDIT_REFUND",
            null,
            "purchase",
            null,
            null);
    Transaction purchase =
        transaction(
            "purchase",
            "CREDIT_PURCHASE",
            "food-child",
            null,
            "Merchant X",
            null);

    when(f.transactionMapper.selectById("refund")).thenReturn(refund);
    when(f.transactionMapper.selectById("purchase")).thenReturn(purchase);
    when(f.entryMapper.selectByTransactionId("refund"))
        .thenReturn(List.of(entry("card-refund", "-30", "JPY")));
    when(f.allocationMapper.selectByTransactionId("refund"))
        .thenReturn(
            List.of(
                allocation("person-a", "18"),
                allocation("person-b", "12")));

    when(f.categoryMapper.selectById("food-child"))
        .thenReturn(category("food-child", "food-parent"));
    when(f.categoryMapper.selectById("food-parent"))
        .thenReturn(category("food-parent", "root"));
    when(f.categoryMapper.selectById("root"))
        .thenReturn(category("root", null));

    BudgetActualFacts facts = f.resolver.resolve("refund");

    assertEquals(new BigDecimal("30"), facts.eligibleAmount());
    assertEquals("JPY", facts.currency());
    assertEquals("food-child", facts.targetFacts().categoryId());
    assertEquals(Set.of("food-parent", "root"), facts.targetFacts().categoryAncestorIds());
    assertEquals(Set.of("card-refund"), facts.targetFacts().accountIds());
    assertEquals("Merchant X", facts.targetFacts().merchant());
    assertEquals(new BigDecimal("18"), facts.targetFacts().personAllocatedAmounts().get("person-a"));
    assertEquals(new BigDecimal("12"), facts.targetFacts().personAllocatedAmounts().get("person-b"));

    // Person facts must not be read from the original purchase; otherwise partial refunds would
    // accidentally reuse the original 60/40 amounts instead of the refund's 18/12.
    verify(f.allocationMapper).selectByTransactionId("refund");
  }

  @Test
  void orphanCreditRefundFailsClosedBecauseDirtyDataMustBeRemoved() {
    Fixture f = fixture();

    Transaction refund =
        transaction(
            "refund",
            "CREDIT_REFUND",
            "food",
            "deleted-purchase",
            "Merchant Snapshot",
            null);
    refund.setOriginCode("CREDIT_REFUND");

    when(f.transactionMapper.selectById("refund")).thenReturn(refund);
    when(f.transactionMapper.selectById("deleted-purchase")).thenReturn(null);
    when(f.entryMapper.selectByTransactionId("refund"))
        .thenReturn(List.of(entry("card", "-30", "JPY")));

    assertThrows(IllegalStateException.class, () -> f.resolver.resolve("refund"));
  }

  @Test
  void ordinaryExpenseUsesItsOwnDimensionsAndSingleEntryAmount() {
    Fixture f = fixture();

    Transaction expense =
        transaction(
            "expense",
            "EXPENSE",
            "food",
            null,
            "Cafe",
            Instant.parse("2026-08-12T02:00:00Z"));
    when(f.transactionMapper.selectById("expense")).thenReturn(expense);
    when(f.entryMapper.selectByTransactionId("expense"))
        .thenReturn(List.of(entry("cash", "-100", "jpy")));
    when(f.allocationMapper.selectByTransactionId("expense"))
        .thenReturn(List.of(allocation("person-a", "100")));
    when(f.categoryMapper.selectById("food")).thenReturn(category("food", null));

    BudgetActualFacts facts = f.resolver.resolve("expense");

    assertEquals(new BigDecimal("100"), facts.eligibleAmount());
    assertEquals("JPY", facts.currency());
    assertEquals("food", facts.targetFacts().categoryId());
    assertEquals(Set.of("cash"), facts.targetFacts().accountIds());
    assertEquals("Cafe", facts.targetFacts().merchant());
    assertEquals(new BigDecimal("100"), facts.targetFacts().personAllocatedAmounts().get("person-a"));
  }

  @Test
  void loanPurchaseUsesLoanEntryAmountAndExpenseDimensions() {
    Fixture f = fixture();

    Transaction purchase =
        transaction(
            "loan-purchase",
            "LOAN_PURCHASE",
            "shopping",
            "loan-account",
            "Store",
            Instant.parse("2026-08-12T03:00:00Z"));
    purchase.setOriginCode("LOAN_PURCHASE");
    when(f.transactionMapper.selectById("loan-purchase")).thenReturn(purchase);
    when(f.entryMapper.selectByTransactionId("loan-purchase"))
        .thenReturn(List.of(entry("loan-account", "25000", "JPY")));
    when(f.allocationMapper.selectByTransactionId("loan-purchase")).thenReturn(List.of());
    when(f.categoryMapper.selectById("shopping")).thenReturn(category("shopping", null));

    BudgetActualFacts facts = f.resolver.resolve("loan-purchase");

    assertEquals(new BigDecimal("25000"), facts.eligibleAmount());
    assertEquals("JPY", facts.currency());
    assertEquals("shopping", facts.targetFacts().categoryId());
    assertEquals("Store", facts.targetFacts().merchant());
    assertEquals(Set.of("loan-account"), facts.targetFacts().accountIds());
  }

  @Test
  void excludedTransferDoesNotRequireSingleEntryAmount() {
    Fixture f = fixture();

    Transaction transfer =
        transaction("transfer", "TRANSFER", null, null, null, null);
    when(f.transactionMapper.selectById("transfer")).thenReturn(transfer);
    when(f.entryMapper.selectByTransactionId("transfer"))
        .thenReturn(
            List.of(
                entry("bank-a", "-100", "JPY"),
                entry("bank-b", "100", "JPY")));
    when(f.allocationMapper.selectByTransactionId("transfer")).thenReturn(List.of());

    BudgetActualFacts facts = f.resolver.resolve("transfer");

    assertEquals(BigDecimal.ZERO, facts.eligibleAmount());
    assertEquals(null, facts.currency());
    assertEquals(Set.of("bank-a", "bank-b"), facts.targetFacts().accountIds());
  }

  @Test
  void statementFeeAndReversalUseTheirSingleLiabilityEntryAsBudgetAmount() {
    Fixture feeFixture = fixture();
    Transaction fee =
        transaction(
            "fee",
            "CREDIT_STATEMENT_FEE",
            null,
            "adjustment",
            null,
            Instant.parse("2026-08-12T04:00:00Z"));
    fee.setOriginCode("STATEMENT_ADJUSTMENT");
    when(feeFixture.transactionMapper.selectById("fee")).thenReturn(fee);
    when(feeFixture.entryMapper.selectByTransactionId("fee"))
        .thenReturn(List.of(entry("card", "120", "JPY")));
    when(feeFixture.allocationMapper.selectByTransactionId("fee")).thenReturn(List.of());

    BudgetActualFacts feeFacts = feeFixture.resolver.resolve("fee");
    assertEquals(new BigDecimal("120"), feeFacts.eligibleAmount());
    assertEquals("JPY", feeFacts.currency());
    assertEquals(Set.of("card"), feeFacts.targetFacts().accountIds());

    Fixture reversalFixture = fixture();
    Transaction reversal =
        transaction(
            "fee-reversal",
            "CREDIT_STATEMENT_FEE",
            null,
            "adjustment",
            null,
            Instant.parse("2026-08-12T05:00:00Z"));
    reversal.setOriginCode("STATEMENT_ADJUSTMENT_REVERSAL");
    when(reversalFixture.transactionMapper.selectById("fee-reversal")).thenReturn(reversal);
    when(reversalFixture.entryMapper.selectByTransactionId("fee-reversal"))
        .thenReturn(List.of(entry("card", "-120", "JPY")));
    when(reversalFixture.allocationMapper.selectByTransactionId("fee-reversal")).thenReturn(List.of());

    BudgetActualFacts reversalFacts = reversalFixture.resolver.resolve("fee-reversal");
    assertEquals(new BigDecimal("120"), reversalFacts.eligibleAmount());
    assertEquals("JPY", reversalFacts.currency());
  }

  @Test
  void unresolvedStatementAdjustmentFailsClosedBeforeFactsAreUsed() {
    Fixture f = fixture();

    Transaction adjustment =
        transaction(
            "adjustment",
            "EXPENSE",
            null,
            null,
            null,
            null);
    adjustment.setOriginCode("STATEMENT_ADJUSTMENT");
    when(f.transactionMapper.selectById("adjustment")).thenReturn(adjustment);

    assertThrows(IllegalStateException.class, () -> f.resolver.resolve("adjustment"));
  }

  @Test
  void categoryCycleFailsClosed() {
    Fixture f = fixture();

    Transaction expense =
        transaction("expense", "EXPENSE", "a", null, null, null);
    when(f.transactionMapper.selectById("expense")).thenReturn(expense);
    when(f.entryMapper.selectByTransactionId("expense"))
        .thenReturn(List.of(entry("cash", "-10", "JPY")));
    when(f.allocationMapper.selectByTransactionId("expense")).thenReturn(List.of());
    when(f.categoryMapper.selectById("a")).thenReturn(category("a", "b"));
    when(f.categoryMapper.selectById("b")).thenReturn(category("b", "a"));

    assertThrows(IllegalStateException.class, () -> f.resolver.resolve("expense"));
  }

  private Fixture fixture() {
    TransactionMapper transactionMapper = mock(TransactionMapper.class);
    TransactionEntryMapper entryMapper = mock(TransactionEntryMapper.class);
    TransactionAllocationMapper allocationMapper = mock(TransactionAllocationMapper.class);
    CategoryMapper categoryMapper = mock(CategoryMapper.class);

    return new Fixture(
        new BudgetActualFactsResolver(
            transactionMapper,
            entryMapper,
            allocationMapper,
            categoryMapper),
        transactionMapper,
        entryMapper,
        allocationMapper,
        categoryMapper);
  }

  private Transaction transaction(
      String id,
      String type,
      String categoryId,
      String originReferenceId,
      String merchant,
      Instant transactionTime) {
    Transaction transaction = new Transaction();
    transaction.setTransactionId(id);
    transaction.setTransactionType(type);
    transaction.setCategoryId(categoryId);
    transaction.setOriginReferenceId(originReferenceId);
    transaction.setMerchant(merchant);
    transaction.setTransactionTime(
        transactionTime == null ? Instant.parse("2026-08-12T00:00:00Z") : transactionTime);
    return transaction;
  }

  private TransactionEntry entry(String accountId, String amount, String currency) {
    TransactionEntry entry = new TransactionEntry();
    entry.setAccountId(accountId);
    entry.setAmount(new BigDecimal(amount));
    entry.setCurrency(currency);
    return entry;
  }

  private TransactionAllocation allocation(String personId, String amount) {
    TransactionAllocation allocation = new TransactionAllocation();
    allocation.setPersonId(personId);
    allocation.setAllocatedAmount(new BigDecimal(amount));
    return allocation;
  }

  private Category category(String id, String parentId) {
    Category category = new Category();
    category.setCategoryId(id);
    category.setParentId(parentId);
    return category;
  }

  private record Fixture(
      BudgetActualFactsResolver resolver,
      TransactionMapper transactionMapper,
      TransactionEntryMapper entryMapper,
      TransactionAllocationMapper allocationMapper,
      CategoryMapper categoryMapper) {}
}
