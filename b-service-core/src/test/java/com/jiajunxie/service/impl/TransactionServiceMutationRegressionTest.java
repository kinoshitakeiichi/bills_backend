package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.convert.TransactionConvertServiceMapper;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.Category;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.mapper.LoanPaymentDetailMapper;
import com.jiajunxie.mapper.PersonMapper;
import com.jiajunxie.mapper.TransactionAllocationMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.CreditCardPaymentPlanService;
import com.jiajunxie.service.StatementPaymentAllocationService;
import com.jiajunxie.vo.TransactionCreateVo;
import com.jiajunxie.vo.TransactionEntryVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionServiceMutationRegressionTest {

  private static final Instant TIME = Instant.parse("2026-08-11T00:00:00Z");

  @Test
  void expenseUpdateRollsBackOldBalanceThenAppliesReplacement() {
    Fixture f = fixture();
    when(f.transactionMapper.selectById("tx")).thenReturn(transaction("tx", "EXPENSE"));
    when(f.entryMapper.selectByTransactionId("tx"))
        .thenReturn(List.of(entry("asset", "-100", "SINGLE")));
    stubAsset(f, "asset");
    stubExpenseCategory(f);
    when(f.transactionMapper.updateById(any(Transaction.class))).thenReturn(1);
    when(f.accountMapper.addBalance(any(), any())).thenReturn(1);
    when(f.entryMapper.insert(any(TransactionEntry.class))).thenReturn(1);
    when(f.paymentPlanService.isLocked("tx")).thenReturn(false);

    int updated = f.service.updateTransaction("tx", expense("-150"));

    assertEquals(1, updated);
    verify(f.accountMapper).addBalance("asset", new BigDecimal("100"));
    verify(f.accountMapper).addBalance("asset", new BigDecimal("-150"));
    verify(f.entryMapper).deleteByTransactionId("tx");
    verify(f.paymentPlanService).replacePlan(eq("tx"), any(TransactionCreateVo.class));
  }

  @Test
  void transferDeleteRollsBackBothAccountBalancesAndRemovesDependents() {
    Fixture f = fixture();
    when(f.transactionMapper.selectById("tx")).thenReturn(transaction("tx", "TRANSFER"));
    when(f.entryMapper.selectByTransactionId("tx"))
        .thenReturn(
            List.of(
                entry("source", "-100", "SOURCE"),
                entry("target", "100", "TARGET")));
    when(f.accountMapper.addBalance(any(), any())).thenReturn(1);
    when(f.transactionMapper.deleteById("tx")).thenReturn(1);

    int deleted = f.service.removeTransaction("tx");

    assertEquals(1, deleted);
    verify(f.accountMapper).addBalance("source", new BigDecimal("100"));
    verify(f.accountMapper).addBalance("target", new BigDecimal("-100"));
    verify(f.statementAllocationService).deleteByPaymentTransactionId("tx");
    verify(f.entryMapper).deleteByTransactionId("tx");
    verify(f.allocationMapper).deleteByTransactionId("tx");
    verify(f.paymentPlanService).removePlan("tx");
  }

  @Test
  void originalExpenseWithRefundReferenceCannotBeDeleted() {
    Fixture f = fixture();
    when(f.transactionMapper.selectById("tx")).thenReturn(transaction("tx", "EXPENSE"));
    when(f.transactionMapper.countRefundReferences("tx")).thenReturn(1L);

    assertThrows(BusinessException.class, () -> f.service.removeTransaction("tx"));

    verify(f.entryMapper, never()).selectByTransactionId("tx");
    verify(f.accountMapper, never()).addBalance(any(), any());
    verify(f.transactionMapper, never()).deleteById("tx");
    verify(f.paymentPlanService, never()).assertMutable("tx");
  }

  @Test
  void originalCreditPurchaseWithCreditRefundReferenceCannotBeDeleted() {
    Fixture f = fixture();
    when(f.transactionMapper.selectById("tx")).thenReturn(transaction("tx", "CREDIT_PURCHASE"));
    when(f.transactionMapper.countRefundReferences("tx")).thenReturn(1L);

    assertThrows(BusinessException.class, () -> f.service.removeTransaction("tx"));

    verify(f.entryMapper, never()).selectByTransactionId("tx");
    verify(f.transactionMapper, never()).deleteById("tx");
    verify(f.paymentPlanService, never()).assertMutable("tx");
  }

  @Test
  void pendingCreditPurchaseUpdateReplacesEntriesAndPaymentPlan() {
    Fixture f = fixture();
    when(f.transactionMapper.selectById("tx")).thenReturn(transaction("tx", "CREDIT_PURCHASE"));
    when(f.entryMapper.selectByTransactionId("tx"))
        .thenReturn(List.of(entry("card", "100", "SINGLE")));
    stubCreditCard(f, "card");
    stubExpenseCategory(f);
    when(f.transactionMapper.updateById(any(Transaction.class))).thenReturn(1);
    when(f.accountMapper.addBalance(any(), any())).thenReturn(1);
    when(f.entryMapper.insert(any(TransactionEntry.class))).thenReturn(1);
    when(f.paymentPlanService.isLocked("tx")).thenReturn(false);

    int updated = f.service.updateTransaction("tx", creditPurchase("120"));

    assertEquals(1, updated);
    verify(f.accountMapper).addBalance("card", new BigDecimal("-100"));
    verify(f.accountMapper).addBalance("card", new BigDecimal("120"));
    verify(f.paymentPlanService).replacePlan(eq("tx"), any(TransactionCreateVo.class));
  }

  @Test
  void lockedCreditPurchaseAllowsMetadataOnlyUpdateWithoutTouchingBalancesOrEntries() {
    Fixture f = fixture();
    Transaction existing = transaction("tx", "CREDIT_PURCHASE");
    existing.setCategoryId("expense-category");
    existing.setMerchant("old merchant");
    existing.setRemark("old remark");

    when(f.transactionMapper.selectById("tx")).thenReturn(existing);
    when(f.entryMapper.selectByTransactionId("tx"))
        .thenReturn(List.of(entry("card", "100", "SINGLE")));
    stubCreditCard(f, "card");
    stubExpenseCategory(f);
    when(f.paymentPlanService.isLocked("tx")).thenReturn(true);
    when(f.paymentPlanService.getPlan("tx")).thenReturn(null);
    when(f.transactionMapper.updateById(any(Transaction.class))).thenReturn(1);

    TransactionCreateVo replacement =
        new TransactionCreateVo(
            null,
            "CREDIT_PURCHASE",
            "expense-category",
            "new merchant",
            "new remark",
            TIME,
            List.of(new TransactionEntryVo("card", new BigDecimal("100"), "JPY", "SINGLE")),
            List.of(),
            null,
            List.of());

    int updated = f.service.updateTransaction("tx", replacement);

    assertEquals(1, updated);
    verify(f.transactionMapper).updateById(any(Transaction.class));
    verify(f.accountMapper, never()).addBalance(any(), any());
    verify(f.entryMapper, never()).deleteByTransactionId(any());
    verify(f.entryMapper, never()).insert(any(TransactionEntry.class));
    verify(f.paymentPlanService, never()).replacePlan(any(), any());
  }

  @Test
  void creditRefundCannotBeUpdatedThroughGenericTransactionApi() {
    Fixture f = fixture();
    when(f.transactionMapper.selectById("tx")).thenReturn(transaction("tx", "CREDIT_REFUND"));

    assertThrows(BusinessException.class, () -> f.service.updateTransaction("tx", expense("-30")));

    verify(f.entryMapper, never()).selectByTransactionId("tx");
    verify(f.transactionMapper, never()).updateById(any(Transaction.class));
    verify(f.accountMapper, never()).addBalance(any(), any());
  }

  @Test
  void creditRefundCannotBeDeletedThroughGenericTransactionApi() {
    Fixture f = fixture();
    when(f.transactionMapper.selectById("tx")).thenReturn(transaction("tx", "CREDIT_REFUND"));

    assertThrows(BusinessException.class, () -> f.service.removeTransaction("tx"));

    verify(f.entryMapper, never()).selectByTransactionId("tx");
    verify(f.transactionMapper, never()).deleteById("tx");
    verify(f.accountMapper, never()).addBalance(any(), any());
  }

  @Test
  void lockedCreditPurchaseRejectsFinancialChangeBeforeAnyMutation() {
    Fixture f = fixture();
    when(f.transactionMapper.selectById("tx")).thenReturn(transaction("tx", "CREDIT_PURCHASE"));
    when(f.entryMapper.selectByTransactionId("tx"))
        .thenReturn(List.of(entry("card", "100", "SINGLE")));
    stubCreditCard(f, "card");
    stubExpenseCategory(f);
    when(f.paymentPlanService.isLocked("tx")).thenReturn(true);
    when(f.paymentPlanService.getPlan("tx")).thenReturn(null);

    assertThrows(BusinessException.class, () -> f.service.updateTransaction("tx", creditPurchase("120")));

    verify(f.transactionMapper, never()).updateById(any(Transaction.class));
    verify(f.accountMapper, never()).addBalance(any(), any());
    verify(f.entryMapper, never()).deleteByTransactionId(any());
  }

  @Test
  void lockedCreditPurchaseDeleteStopsBeforeBalanceRollback() {
    Fixture f = fixture();
    when(f.transactionMapper.selectById("tx")).thenReturn(transaction("tx", "CREDIT_PURCHASE"));
    doThrow(new BusinessException(Integer.valueOf(409), "locked"))
        .when(f.paymentPlanService)
        .assertMutable("tx");

    assertThrows(BusinessException.class, () -> f.service.removeTransaction("tx"));

    verify(f.entryMapper, never()).selectByTransactionId("tx");
    verify(f.accountMapper, never()).addBalance(any(), any());
    verify(f.transactionMapper, never()).deleteById("tx");
  }

  private TransactionCreateVo expense(String amount) {
    return new TransactionCreateVo(
        null,
        "EXPENSE",
        "expense-category",
        "merchant",
        "remark",
        TIME,
        List.of(new TransactionEntryVo("asset", new BigDecimal(amount), "JPY", "SINGLE")),
        List.of(),
        null,
        List.of());
  }

  private TransactionCreateVo creditPurchase(String amount) {
    return new TransactionCreateVo(
        null,
        "CREDIT_PURCHASE",
        "expense-category",
        "merchant",
        "remark",
        TIME,
        List.of(new TransactionEntryVo("card", new BigDecimal(amount), "JPY", "SINGLE")),
        List.of(),
        null,
        List.of());
  }

  private void stubAsset(Fixture f, String id) {
    Account account = new Account();
    account.setAccountId(id);
    account.setAccountClassCode("ASSET");
    account.setAccountTypeCode("BANK");
    account.setCurrency("JPY");
    account.setEnabled(true);
    when(f.accountMapper.selectById(id)).thenReturn(account);
  }

  private void stubCreditCard(Fixture f, String id) {
    Account account = new Account();
    account.setAccountId(id);
    account.setAccountClassCode("LIABILITY");
    account.setAccountTypeCode("CREDIT_CARD");
    account.setCurrency("JPY");
    account.setEnabled(true);
    when(f.accountMapper.selectById(id)).thenReturn(account);
  }

  private void stubExpenseCategory(Fixture f) {
    Category category = new Category();
    category.setCategoryId("expense-category");
    category.setCategoryTypeCode("EXPENSE");
    category.setEnabled(true);
    when(f.categoryMapper.selectById("expense-category")).thenReturn(category);
    when(f.categoryMapper.selectCount(any())).thenReturn(0L);
  }

  private Transaction transaction(String id, String type) {
    Transaction t = new Transaction();
    t.setTransactionId(id);
    t.setTransactionType(type);
    t.setTransactionTime(TIME);
    t.setCreatedAt(TIME);
    return t;
  }

  private TransactionEntry entry(String accountId, String amount, String role) {
    TransactionEntry e = new TransactionEntry();
    e.setEntryId(accountId + "-entry");
    e.setTransactionId("tx");
    e.setAccountId(accountId);
    e.setAmount(new BigDecimal(amount));
    e.setCurrency("JPY");
    e.setEntryRole(role);
    return e;
  }

  private Fixture fixture() {
    TransactionMapper transactionMapper = mock(TransactionMapper.class);
    TransactionEntryMapper entryMapper = mock(TransactionEntryMapper.class);
    AccountMapper accountMapper = mock(AccountMapper.class);
    TransactionConvertServiceMapper convertMapper = mock(TransactionConvertServiceMapper.class);
    TransactionAllocationMapper allocationMapper = mock(TransactionAllocationMapper.class);
    PersonMapper personMapper = mock(PersonMapper.class);
    CategoryMapper categoryMapper = mock(CategoryMapper.class);
    CreditCardPaymentPlanService paymentPlanService = mock(CreditCardPaymentPlanService.class);
    StatementPaymentAllocationService statementAllocationService =
        mock(StatementPaymentAllocationService.class);

    when(convertMapper.toEntity(any(TransactionCreateVo.class)))
        .thenAnswer(
            invocation -> {
              TransactionCreateVo vo = invocation.getArgument(0);
              Transaction t = new Transaction();
              t.setTransactionType(vo.transactionType());
              t.setCategoryId(vo.categoryId());
              t.setMerchant(vo.merchant());
              t.setRemark(vo.remark());
              t.setTransactionTime(vo.transactionTime());
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
            allocationMapper,
            personMapper,
            categoryMapper,
            paymentPlanService,
            statementAllocationService,
            mock(LoanPaymentDetailMapper.class), new TransactionMutationPolicy(), org.mockito.Mockito.mock(ForeignCurrencyPositionEngine.class));

    return new Fixture(
        service,
        transactionMapper,
        entryMapper,
        accountMapper,
        allocationMapper,
        categoryMapper,
        paymentPlanService,
        statementAllocationService);
  }

  private record Fixture(
      TransactionServiceImpl service,
      TransactionMapper transactionMapper,
      TransactionEntryMapper entryMapper,
      AccountMapper accountMapper,
      TransactionAllocationMapper allocationMapper,
      CategoryMapper categoryMapper,
      CreditCardPaymentPlanService paymentPlanService,
      StatementPaymentAllocationService statementAllocationService) {}
}
