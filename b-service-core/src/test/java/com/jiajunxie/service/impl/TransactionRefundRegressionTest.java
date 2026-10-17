package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

class TransactionRefundRegressionTest {

  @Test
  void assetRefundAddsBalanceAndUsesExpenseCategory() {
    Fixture f = fixture();
    when(f.accountMapper.selectById("asset")).thenReturn(account("asset", "ASSET", "BANK"));
    when(f.categoryMapper.selectById("expense-category")).thenReturn(expenseCategory());
    when(f.categoryMapper.selectCount(any())).thenReturn(0L);
    when(f.transactionMapper.insert(any(Transaction.class))).thenReturn(1);
    when(f.entryMapper.insert(any(TransactionEntry.class))).thenReturn(1);
    when(f.accountMapper.addBalance(any(), any())).thenReturn(1);

    f.service.createTransaction(refund("asset", "75"));

    verify(f.accountMapper).addBalance("asset", new BigDecimal("75"));
    verify(f.paymentPlanService).createPlan(any(), any());
  }

  @Test
  void refundRejectsLiabilityAccount() {
    Fixture f = fixture();
    when(f.accountMapper.selectById("card")).thenReturn(account("card", "LIABILITY", "CREDIT_CARD"));
    when(f.categoryMapper.selectById("expense-category")).thenReturn(expenseCategory());
    when(f.categoryMapper.selectCount(any())).thenReturn(0L);

    assertThrows(
        BusinessException.class,
        () -> f.service.createTransaction(refund("card", "75")));

    verify(f.transactionMapper, org.mockito.Mockito.never()).insert(any(Transaction.class));
  }

  @Test
  void refundRequiresExpenseCategory() {
    Fixture f = fixture();
    when(f.accountMapper.selectById("asset")).thenReturn(account("asset", "ASSET", "BANK"));
    Category income = new Category();
    income.setCategoryId("expense-category");
    income.setCategoryTypeCode("INCOME");
    income.setEnabled(true);
    when(f.categoryMapper.selectById("expense-category")).thenReturn(income);

    assertThrows(
        BusinessException.class,
        () -> f.service.createTransaction(refund("asset", "75")));
  }

  private TransactionCreateVo refund(String accountId, String amount) {
    return new TransactionCreateVo(
        null,
        "REFUND",
        "expense-category",
        "Merchant",
        "refund",
        Instant.parse("2026-08-11T00:00:00Z"),
        List.of(new TransactionEntryVo(
            accountId, new BigDecimal(amount), "JPY", "SINGLE")),
        List.of(),
        null,
        List.of());
  }

  private Account account(String id, String accountClass, String type) {
    Account account = new Account();
    account.setAccountId(id);
    account.setAccountClassCode(accountClass);
    account.setAccountTypeCode(type);
    account.setCurrency("JPY");
    account.setEnabled(true);
    return account;
  }

  private Category expenseCategory() {
    Category category = new Category();
    category.setCategoryId("expense-category");
    category.setCategoryTypeCode("EXPENSE");
    category.setEnabled(true);
    return category;
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
        .thenAnswer(invocation -> {
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
        .thenAnswer(invocation -> {
          TransactionEntryVo vo = invocation.getArgument(0);
          TransactionEntry e = new TransactionEntry();
          e.setAccountId(vo.accountId());
          e.setAmount(vo.amount());
          e.setCurrency(vo.currency());
          e.setEntryRole(vo.entryRole());
          return e;
        });

    TransactionServiceImpl service = new TransactionServiceImpl(
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
        service, transactionMapper, entryMapper, accountMapper,
        categoryMapper, paymentPlanService);
  }

  private record Fixture(
      TransactionServiceImpl service,
      TransactionMapper transactionMapper,
      TransactionEntryMapper entryMapper,
      AccountMapper accountMapper,
      CategoryMapper categoryMapper,
      CreditCardPaymentPlanService paymentPlanService) {}
}
