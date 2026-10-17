package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.Category;
import com.jiajunxie.entity.LoanProfile;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.entity.TransactionAllocation;
import com.jiajunxie.entity.Person;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.mapper.LoanProfileMapper;
import com.jiajunxie.mapper.InstallmentPlanMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.mapper.TransactionAllocationMapper;
import com.jiajunxie.mapper.PersonMapper;
import com.jiajunxie.vo.LoanPurchaseCreateVo;
import com.jiajunxie.vo.AllocationVo;
import java.util.List;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class LoanPurchaseServiceImplTest {
  @Mock TransactionMapper transactionMapper;
  @Mock TransactionEntryMapper entryMapper;
  @Mock AccountMapper accountMapper;
  @Mock LoanProfileMapper profileMapper;
  @Mock CategoryMapper categoryMapper;
  @Mock InstallmentPlanMapper installmentPlanMapper;
  @Mock TransactionAllocationMapper allocationMapper;
  @Mock PersonMapper personMapper;
  @Mock LoanLifecycleDeletionGuard loanLifecycleDeletionGuard;
  LoanPurchaseServiceImpl service;

  @BeforeEach void setUp(){ MockitoAnnotations.openMocks(this); service=new LoanPurchaseServiceImpl(transactionMapper,entryMapper,accountMapper,profileMapper,categoryMapper,installmentPlanMapper,allocationMapper,personMapper,loanLifecycleDeletionGuard); }

  @Test void paidyPurchaseIncreasesLoanWithoutAssetEntry(){
    stub("PURCHASE_FINANCING", null);
    when(transactionMapper.insert(any(Transaction.class))).thenReturn(1);
    when(entryMapper.insert(any(TransactionEntry.class))).thenReturn(1);
    when(accountMapper.addBalance(eq("loan"), any(BigDecimal.class))).thenReturn(1);
    var result=service.create(command("30000"));
    assertEquals(new BigDecimal("30000"), result.amount());
    ArgumentCaptor<TransactionEntry> cap=ArgumentCaptor.forClass(TransactionEntry.class);
    verify(entryMapper).insert(cap.capture());
    assertEquals("loan", cap.getValue().getAccountId());
    assertEquals(new BigDecimal("30000"), cap.getValue().getAmount());
    verify(accountMapper).addBalance("loan", new BigDecimal("30000"));
  }

  @Test void purchasePersistsPersonAllocations(){
    stub("PURCHASE_FINANCING", null);
    Person person = new Person(); person.setPersonId("lucia"); person.setEnabled(true);
    when(personMapper.selectById("lucia")).thenReturn(person);
    when(transactionMapper.insert(any(Transaction.class))).thenReturn(1);
    when(entryMapper.insert(any(TransactionEntry.class))).thenReturn(1);
    when(allocationMapper.insert(any(TransactionAllocation.class))).thenReturn(1);
    when(accountMapper.addBalance(eq("loan"), any(BigDecimal.class))).thenReturn(1);
    var command = new LoanPurchaseCreateVo(
        "loan", "shopping", new BigDecimal("30000"), "JPY", "merchant", null,
        Instant.parse("2026-08-11T00:00:00Z"),
        List.of(new AllocationVo("lucia", new BigDecimal("30000"), "JPY", null)));
    service.create(command);
    ArgumentCaptor<TransactionAllocation> cap=ArgumentCaptor.forClass(TransactionAllocation.class);
    verify(allocationMapper).insert(cap.capture());
    assertEquals("lucia", cap.getValue().getPersonId());
    assertEquals(new BigDecimal("30000"), cap.getValue().getAllocatedAmount());
  }

  @Test void directFinancingMustEqualOriginalPrincipalAndOnlyOnce(){
    stub("DIRECT_FINANCING", new BigDecimal("300000"));
    assertThrows(BusinessException.class,()->service.create(command("200000")));
    verify(transactionMapper, never()).insert(any(Transaction.class));
  }

  @Test void accountDisbursementModeRejectsLoanPurchase(){
    stub("ACCOUNT_DISBURSEMENT", new BigDecimal("300000"));
    assertThrows(BusinessException.class,()->service.create(command("300000")));
  }

@Test
void removeUnpaidPurchaseRestoresLoanBalance(){
  Transaction tx = new Transaction();
  tx.setTransactionId("purchase");
  tx.setTransactionType("LOAN_PURCHASE");
  tx.setOriginCode("LOAN_ENGINE");
  tx.setOriginReferenceId("loan");
  TransactionEntry entry = new TransactionEntry();
  entry.setTransactionId("purchase");
  entry.setAccountId("loan");
  entry.setEntryRole("SINGLE");
  entry.setAmount(new BigDecimal("30000"));
  entry.setCurrency("JPY");

  when(transactionMapper.selectById("purchase")).thenReturn(tx);
  when(installmentPlanMapper.selectCount(any())).thenReturn(0L);
  when(entryMapper.selectList(any())).thenReturn(List.of(entry));
  when(accountMapper.addBalance("loan", new BigDecimal("-30000"))).thenReturn(1);
  when(transactionMapper.deleteById("purchase")).thenReturn(1);

  assertEquals(1, service.remove("purchase"));

  verify(loanLifecycleDeletionGuard).assertLoanAccountDeletable("loan");
  verify(allocationMapper).deleteByTransactionId("purchase");
  verify(entryMapper).deleteByTransactionId("purchase");
}

  private void stub(String mode, BigDecimal principal){
    Account a=new Account(); a.setAccountId("loan"); a.setAccountClassCode("LIABILITY"); a.setAccountTypeCode("CONSUMER_LOAN"); a.setCurrency("JPY"); a.setEnabled(true);
    LoanProfile p=new LoanProfile(); p.setAccountId("loan"); p.setFundingModeCode(mode); p.setCurrency("JPY"); p.setOriginalPrincipal(principal);
    Category c=new Category(); c.setCategoryId("shopping"); c.setCategoryTypeCode("EXPENSE"); c.setEnabled(true);
    when(accountMapper.selectById("loan")).thenReturn(a); when(profileMapper.selectById("loan")).thenReturn(p); when(categoryMapper.selectById("shopping")).thenReturn(c); when(transactionMapper.selectCount(any())).thenReturn(0L);
  }
  private LoanPurchaseCreateVo command(String amount){return new LoanPurchaseCreateVo("loan","shopping",new BigDecimal(amount),"JPY","merchant",null,Instant.parse("2026-08-11T00:00:00Z"),List.of());}
}
