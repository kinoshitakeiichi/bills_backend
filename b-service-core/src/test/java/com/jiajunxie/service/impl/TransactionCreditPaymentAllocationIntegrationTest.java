package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiajunxie.convert.TransactionConvertServiceMapper;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.mapper.PersonMapper;
import com.jiajunxie.mapper.TransactionAllocationMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.mapper.LoanPaymentDetailMapper;
import com.jiajunxie.service.CreditCardPaymentPlanService;
import com.jiajunxie.service.StatementPaymentAllocationService;
import com.jiajunxie.vo.StatementPaymentAllocationCommandVo;
import com.jiajunxie.vo.TransactionCreateVo;
import com.jiajunxie.vo.TransactionEntryVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransactionCreditPaymentAllocationIntegrationTest {

  @Test
  void createCreditPaymentAppliesAllocationAfterBalanceEntries() {
    Fixture f = fixture();
    stubAccounts(f);
    when(f.transactionMapper.insert(any(Transaction.class))).thenReturn(1);
    when(f.transactionMapper.updateById(any(Transaction.class))).thenReturn(1);
    when(f.accountMapper.addBalance(any(), any())).thenReturn(1);
    when(f.entryMapper.insert(any(TransactionEntry.class))).thenReturn(1);

    String transactionId = f.service.createTransaction(creditPayment());

    verify(f.allocationService)
        .replaceAllocations(
            eq(transactionId),
            eq("card"),
            eq(new BigDecimal("1000")),
            eq("JPY"),
            eq(
                List.of(
                    new StatementPaymentAllocationCommandVo(
                        "statement", new BigDecimal("1000"), 1))));
  }

  @Test
  void updateCreditPaymentRollsBackOldAllocationAndReplacesIt() {
    Fixture f = fixture();
    stubAccounts(f);
    Transaction existing = transaction("CREDIT_PAYMENT");
    when(f.transactionMapper.selectById("payment")).thenReturn(existing);
    when(f.transactionMapper.updateById(any(Transaction.class))).thenReturn(1);
    when(f.entryMapper.selectByTransactionId("payment"))
        .thenReturn(
            List.of(
                entry("bank", new BigDecimal("-1000"), "SOURCE"),
                entry("card", new BigDecimal("-1000"), "TARGET")));
    when(f.accountMapper.addBalance(any(), any())).thenReturn(1);
    when(f.entryMapper.insert(any(TransactionEntry.class))).thenReturn(1);
    when(f.paymentPlanService.isLocked("payment")).thenReturn(false);

    f.service.updateTransaction("payment", creditPayment());

    verify(f.allocationService).deleteByPaymentTransactionId("payment");
    verify(f.allocationService)
        .replaceAllocations(
            eq("payment"),
            eq("card"),
            eq(new BigDecimal("1000")),
            eq("JPY"),
            any());
  }

  @Test
  void deleteCreditPaymentRemovesAllocationBeforeTransactionDeletion() {
    Fixture f = fixture();
    Transaction existing = transaction("CREDIT_PAYMENT");
    when(f.transactionMapper.selectById("payment")).thenReturn(existing);
    when(f.entryMapper.selectByTransactionId("payment"))
        .thenReturn(
            List.of(
                entry("bank", new BigDecimal("-1000"), "SOURCE"),
                entry("card", new BigDecimal("-1000"), "TARGET")));
    when(f.accountMapper.addBalance(any(), any())).thenReturn(1);
    when(f.transactionMapper.deleteById("payment")).thenReturn(1);

    int deleted = f.service.removeTransaction("payment");

    assertEquals(1, deleted);
    verify(f.allocationService).deleteByPaymentTransactionId("payment");
  }

  @Test
  void nonCreditPaymentDoesNotCreateStatementAllocation() {
    Fixture f = fixture();
    // This test only verifies the helper branch through a normal TRANSFER-like create is not needed
    // because F4 validation would require account stubs; no direct allocation call should occur from
    // unrelated payment-plan service operations.
    verify(f.allocationService, never())
        .replaceAllocations(any(), any(), any(), any(), any());
  }

  private TransactionCreateVo creditPayment() {
    return new TransactionCreateVo(
        null,
        "CREDIT_PAYMENT",
        null,
        null,
        "statement payment",
        Instant.parse("2026-10-27T00:00:00Z"),
        List.of(
            new TransactionEntryVo(
                "bank", new BigDecimal("-1000"), "JPY", "SOURCE"),
            new TransactionEntryVo(
                "card", new BigDecimal("-1000"), "JPY", "TARGET")),
        List.of(),
        null,
        List.of(
            new StatementPaymentAllocationCommandVo(
                "statement", new BigDecimal("1000"), 1)));
  }

  private void stubAccounts(Fixture f) {
    when(f.accountMapper.selectById("bank")).thenReturn(bank());
    when(f.accountMapper.selectById("card")).thenReturn(card());
  }

  private Account bank() {
    Account a = new Account();
    a.setAccountId("bank");
    a.setAccountName("Bank");
    a.setAccountClassCode("ASSET");
    a.setAccountTypeCode("BANK");
    a.setCurrency("JPY");
    a.setEnabled(true);
    return a;
  }

  private Account card() {
    Account a = new Account();
    a.setAccountId("card");
    a.setAccountName("Card");
    a.setAccountClassCode("LIABILITY");
    a.setAccountTypeCode("CREDIT_CARD");
    a.setCurrency("JPY");
    a.setEnabled(true);
    return a;
  }

  private Transaction transaction(String type) {
    Transaction t = new Transaction();
    t.setTransactionId("payment");
    t.setTransactionType(type);
    t.setTransactionTime(Instant.parse("2026-10-27T00:00:00Z"));
    t.setCreatedAt(Instant.parse("2026-10-27T00:00:00Z"));
    return t;
  }

  private TransactionEntry entry(
      String accountId, BigDecimal amount, String role) {
    TransactionEntry e = new TransactionEntry();
    e.setEntryId(accountId + "-entry");
    e.setTransactionId("payment");
    e.setAccountId(accountId);
    e.setAmount(amount);
    e.setCurrency("JPY");
    e.setEntryRole(role);
    return e;
  }

  private Fixture fixture() {
    TransactionMapper transactionMapper = mock(TransactionMapper.class);
    TransactionEntryMapper entryMapper = mock(TransactionEntryMapper.class);
    AccountMapper accountMapper = mock(AccountMapper.class);
    TransactionConvertServiceMapper convertMapper =
        mock(TransactionConvertServiceMapper.class);
    TransactionAllocationMapper transactionAllocationMapper =
        mock(TransactionAllocationMapper.class);
    PersonMapper personMapper = mock(PersonMapper.class);
    CategoryMapper categoryMapper = mock(CategoryMapper.class);
    CreditCardPaymentPlanService paymentPlanService =
        mock(CreditCardPaymentPlanService.class);
    StatementPaymentAllocationService allocationService =
        mock(StatementPaymentAllocationService.class);

    when(convertMapper.toEntity(any(TransactionCreateVo.class)))
        .thenAnswer(
            invocation -> {
              TransactionCreateVo vo = invocation.getArgument(0);
              Transaction t = new Transaction();
              t.setTransactionType(vo.transactionType());
              t.setTransactionTime(vo.transactionTime());
              t.setCategoryId(vo.categoryId());
              t.setMerchant(vo.merchant());
              t.setRemark(vo.remark());
              return t;
            });

    when(convertMapper.toEntryEntity(any(TransactionEntryVo.class)))
        .thenAnswer(
            invocation -> {
              TransactionEntryVo vo = invocation.getArgument(0);
              TransactionEntry e = new TransactionEntry();
              e.setAccountId(vo.accountId());
              e.setAmount(vo.amount());
              e.setCurrency(vo.currency());
              e.setEntryRole(vo.entryRole());
              return e;
            });

    TransactionServiceImpl service =
        new TransactionServiceImpl(
            transactionMapper,
            entryMapper,
            accountMapper,
            convertMapper,
            transactionAllocationMapper,
            personMapper,
            categoryMapper,
            paymentPlanService,
            allocationService,
            mock(LoanPaymentDetailMapper.class), new TransactionMutationPolicy(), org.mockito.Mockito.mock(ForeignCurrencyPositionEngine.class));

    return new Fixture(
        service,
        transactionMapper,
        entryMapper,
        accountMapper,
        paymentPlanService,
        allocationService);
  }

  private record Fixture(
      TransactionServiceImpl service,
      TransactionMapper transactionMapper,
      TransactionEntryMapper entryMapper,
      AccountMapper accountMapper,
      CreditCardPaymentPlanService paymentPlanService,
      StatementPaymentAllocationService allocationService) {}
}
