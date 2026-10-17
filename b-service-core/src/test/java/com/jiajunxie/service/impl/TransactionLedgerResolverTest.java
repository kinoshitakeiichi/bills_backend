package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiajunxie.domain.transaction.TransactionNature;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionLedgerResolverTest {

  private final TransactionNatureResolver natureResolver = new TransactionNatureResolver();
  private final TransactionDisplayAmountResolver amountResolver =
      new TransactionDisplayAmountResolver();
  private final TransactionReadonlyResolver readonlyResolver = new TransactionReadonlyResolver(new TransactionMutationPolicy());

  @Test
  void natureMappingKeepsLifecycleSemanticsStable() {
    assertEquals(TransactionNature.INCOME, natureResolver.resolve("INCOME"));
    assertEquals(TransactionNature.REFUND, natureResolver.resolve("REFUND"));
    assertEquals("IN", natureResolver.flowDirection(TransactionNature.REFUND));
    assertEquals(TransactionNature.EXPENSE, natureResolver.resolve("EXPENSE"));
    assertEquals(TransactionNature.EXPENSE, natureResolver.resolve("CREDIT_PURCHASE"));
    assertEquals(TransactionNature.INTERNAL_TRANSFER, natureResolver.resolve("TRANSFER"));
    assertEquals(TransactionNature.LIABILITY_PAYMENT, natureResolver.resolve("CREDIT_PAYMENT"));
    assertEquals(TransactionNature.LIABILITY_PAYMENT, natureResolver.resolve("LOAN_PAYMENT"));
    assertEquals(TransactionNature.FINANCING, natureResolver.resolve("LOAN_DISBURSEMENT"));
    assertEquals(TransactionNature.FX_EXCHANGE, natureResolver.resolve("EXCHANGE"));
  }

  @Test
  void transferDisplayAmountUsesSourceAbsoluteAmountInsteadOfNetZero() {
    List<TransactionEntry> entries =
        List.of(
            entry("SOURCE", "-50000", "JPY"),
            entry("TARGET", "50000", "JPY"));

    assertEquals(new BigDecimal("50000"), amountResolver.resolve("TRANSFER", entries));
    assertEquals("JPY", amountResolver.resolveCurrency("TRANSFER", entries));
  }

  @Test
  void loanPaymentDisplayAmountUsesActualCashSourceAmount() {
    List<TransactionEntry> entries =
        List.of(
            entry("SOURCE", "-20610", "JPY"),
            entry("TARGET", "-20000", "JPY"));

    assertEquals(new BigDecimal("20610"), amountResolver.resolve("LOAN_PAYMENT", entries));
  }

  @Test
  void loanFxAndStatementLifecycleFactsAreReadonly() {
    Transaction loan = transaction("LOAN_PAYMENT", "LOAN_ENGINE");
    Transaction fx = transaction("EXCHANGE", "FX_ENGINE");
    Transaction creditRefund = transaction("CREDIT_REFUND", "CREDIT_REFUND");
    Transaction statement = transaction("EXPENSE", "STATEMENT_ADJUSTMENT");
    Transaction manual = transaction("EXPENSE", null);

    assertTrue(readonlyResolver.isReadonly(loan));
    assertTrue(readonlyResolver.isReadonly(fx));
    assertTrue(readonlyResolver.isReadonly(creditRefund));
    assertTrue(readonlyResolver.isReadonly(statement));
    assertFalse(readonlyResolver.isReadonly(manual));
  }

  private TransactionEntry entry(String role, String amount, String currency) {
    TransactionEntry entry = new TransactionEntry();
    entry.setEntryRole(role);
    entry.setAmount(new BigDecimal(amount));
    entry.setCurrency(currency);
    return entry;
  }

  private Transaction transaction(String type, String origin) {
    Transaction transaction = new Transaction();
    transaction.setTransactionType(type);
    transaction.setOriginCode(origin);
    return transaction;
  }
}
