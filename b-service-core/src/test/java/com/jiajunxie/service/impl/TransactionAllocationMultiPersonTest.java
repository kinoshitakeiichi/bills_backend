package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.convert.TransactionConvertServiceMapper;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.Category;
import com.jiajunxie.entity.Person;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionAllocation;
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
import com.jiajunxie.vo.AllocationVo;
import com.jiajunxie.vo.TransactionCreateVo;
import com.jiajunxie.vo.TransactionEntryVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionAllocationMultiPersonTest {

  @Test
  void expenseSupportsTwoPersonAmountAndRatioAllocation() {
    Fixture f = fixture();
    stubExpenseFacts(f);
    stubPerson(f, "p1");
    stubPerson(f, "p2");

    f.service.createTransaction(
        expense(
            List.of(
                allocation("p1", "60", "0.6"),
                allocation("p2", "40", "0.4"))));

    verify(f.allocationMapper, times(2)).insert(any(TransactionAllocation.class));
  }

  @Test
  void rejectsAllocationWhenAmountSumDoesNotEqualTransactionAmount() {
    Fixture f = fixture();
    stubExpenseFacts(f);
    stubPerson(f, "p1");
    stubPerson(f, "p2");

    assertThrows(
        BusinessException.class,
        () ->
            f.service.createTransaction(
                expense(
                    List.of(
                        allocation("p1", "50", "0.5"),
                        allocation("p2", "40", "0.5")))));

    verify(f.transactionMapper, never()).insert(any(Transaction.class));
  }

  @Test
  void rejectsDuplicatePersonAllocationBeforeWrites() {
    Fixture f = fixture();
    stubExpenseFacts(f);
    stubPerson(f, "p1");

    assertThrows(
        BusinessException.class,
        () ->
            f.service.createTransaction(
                expense(
                    List.of(
                        allocation("p1", "60", "0.6"),
                        allocation("p1", "40", "0.4")))));

    verify(f.transactionMapper, never()).insert(any(Transaction.class));
  }

  @Test
  void rejectsPartialRatioInput() {
    Fixture f = fixture();
    stubExpenseFacts(f);
    stubPerson(f, "p1");
    stubPerson(f, "p2");

    assertThrows(
        BusinessException.class,
        () ->
            f.service.createTransaction(
                expense(
                    List.of(
                        allocation("p1", "60", "0.6"),
                        new AllocationVo("p2", new BigDecimal("40"), "JPY", null)))));

    verify(f.transactionMapper, never()).insert(any(Transaction.class));
  }

  @Test
  void transferStillRejectsPersonAllocations() {
    Fixture f = fixture();
    stubPerson(f, "p1");
    stubAsset(f, "source");
    stubAsset(f, "target");

    TransactionCreateVo transfer =
        new TransactionCreateVo(
            null,
            "TRANSFER",
            null,
            null,
            null,
            Instant.parse("2026-08-12T00:00:00Z"),
            List.of(
                new TransactionEntryVo("source", new BigDecimal("-100"), "JPY", "SOURCE"),
                new TransactionEntryVo("target", new BigDecimal("100"), "JPY", "TARGET")),
            List.of(allocation("p1", "100", "1")),
            null,
            List.of());

    assertThrows(BusinessException.class, () -> f.service.createTransaction(transfer));
    verify(f.transactionMapper, never()).insert(any(Transaction.class));
  }

  private TransactionCreateVo expense(List<AllocationVo> allocations) {
    return new TransactionCreateVo(
        null,
        "EXPENSE",
        "expense-category",
        "merchant",
        "remark",
        Instant.parse("2026-08-12T00:00:00Z"),
        List.of(new TransactionEntryVo("asset", new BigDecimal("-100"), "JPY", "SINGLE")),
        allocations,
        null,
        List.of());
  }

  private AllocationVo allocation(String personId, String amount, String ratio) {
    return new AllocationVo(
        personId,
        new BigDecimal(amount),
        "JPY",
        ratio == null ? null : new BigDecimal(ratio));
  }

  private void stubExpenseFacts(Fixture f) {
    stubAsset(f, "asset");
    Category category = new Category();
    category.setCategoryId("expense-category");
    category.setCategoryTypeCode("EXPENSE");
    category.setEnabled(true);
    when(f.categoryMapper.selectById("expense-category")).thenReturn(category);
    when(f.categoryMapper.selectCount(any())).thenReturn(0L);
    when(f.transactionMapper.insert(any(Transaction.class))).thenReturn(1);
    when(f.entryMapper.insert(any(TransactionEntry.class))).thenReturn(1);
    when(f.accountMapper.addBalance(any(), any())).thenReturn(1);
    when(f.allocationMapper.insert(any(TransactionAllocation.class))).thenReturn(1);
  }

  private void stubAsset(Fixture f, String accountId) {
    Account account = new Account();
    account.setAccountId(accountId);
    account.setAccountClassCode("ASSET");
    account.setAccountTypeCode("BANK");
    account.setCurrency("JPY");
    account.setEnabled(true);
    when(f.accountMapper.selectById(accountId)).thenReturn(account);
  }

  private void stubPerson(Fixture f, String personId) {
    Person person = new Person();
    person.setPersonId(personId);
    person.setEnabled(true);
    when(f.personMapper.selectById(personId)).thenReturn(person);
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

    when(convertMapper.toEntity(any(TransactionCreateVo.class)))
        .thenAnswer(invocation -> {
          TransactionCreateVo vo = invocation.getArgument(0);
          Transaction transaction = new Transaction();
          transaction.setTransactionType(vo.transactionType());
          transaction.setCategoryId(vo.categoryId());
          transaction.setTransactionTime(vo.transactionTime());
          return transaction;
        });
    when(convertMapper.toEntryEntity(any(TransactionEntryVo.class)))
        .thenAnswer(invocation -> {
          TransactionEntryVo vo = invocation.getArgument(0);
          TransactionEntry entry = new TransactionEntry();
          entry.setAccountId(vo.accountId());
          entry.setAmount(vo.amount());
          entry.setCurrency(vo.currency());
          entry.setEntryRole(vo.entryRole());
          return entry;
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
            mock(StatementPaymentAllocationService.class),
            mock(LoanPaymentDetailMapper.class), new TransactionMutationPolicy(), org.mockito.Mockito.mock(ForeignCurrencyPositionEngine.class));

    return new Fixture(
        service,
        transactionMapper,
        entryMapper,
        accountMapper,
        allocationMapper,
        personMapper,
        categoryMapper);
  }

  private record Fixture(
      TransactionServiceImpl service,
      TransactionMapper transactionMapper,
      TransactionEntryMapper entryMapper,
      AccountMapper accountMapper,
      TransactionAllocationMapper allocationMapper,
      PersonMapper personMapper,
      CategoryMapper categoryMapper) {}
}
