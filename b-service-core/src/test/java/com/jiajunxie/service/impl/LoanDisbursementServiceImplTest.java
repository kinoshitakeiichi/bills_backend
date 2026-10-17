package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.LoanProfile;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.LoanProfileMapper;
import com.jiajunxie.mapper.InstallmentPlanMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.vo.LoanDisbursementCreateVo;
import java.math.BigDecimal;
import java.util.List;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class LoanDisbursementServiceImplTest {
  @Mock TransactionMapper transactionMapper;
  @Mock TransactionEntryMapper entryMapper;
  @Mock AccountMapper accountMapper;
  @Mock LoanProfileMapper profileMapper;
  @Mock InstallmentPlanMapper installmentPlanMapper;
  @Mock LoanLifecycleDeletionGuard loanLifecycleDeletionGuard;

  LoanDisbursementServiceImpl service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new LoanDisbursementServiceImpl(transactionMapper, entryMapper, accountMapper, profileMapper, installmentPlanMapper, loanLifecycleDeletionGuard, org.mockito.Mockito.mock(ForeignCurrencyPositionEngine.class));
  }

  @Test
  void postsAssetAndLiabilityIncreaseAtomically() {
    Account loan = loanAccount("loan", "JPY");
    Account bank = assetAccount("bank", "JPY");
    LoanProfile profile = profile("loan", "JPY", "1000000");
    when(accountMapper.selectById("loan")).thenReturn(loan);
    when(accountMapper.selectById("bank")).thenReturn(bank);
    when(profileMapper.selectById("loan")).thenReturn(profile);
    when(transactionMapper.selectCount(any())).thenReturn(0L);
    when(transactionMapper.insert(any(Transaction.class))).thenReturn(1);
    when(entryMapper.insert(any(TransactionEntry.class))).thenReturn(1);
    when(accountMapper.addBalance(eq("bank"), any(BigDecimal.class))).thenReturn(1);
    when(accountMapper.addBalance(eq("loan"), any(BigDecimal.class))).thenReturn(1);

    var result = service.create(command("1000000"));

    assertEquals("loan", result.loanAccountId());
    assertEquals("bank", result.targetAccountId());
    ArgumentCaptor<Transaction> tx = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionMapper).insert(tx.capture());
    assertEquals("LOAN_DISBURSEMENT", tx.getValue().getTransactionType());
    assertEquals("LOAN_ENGINE", tx.getValue().getOriginCode());

    ArgumentCaptor<TransactionEntry> entries = ArgumentCaptor.forClass(TransactionEntry.class);
    verify(entryMapper, org.mockito.Mockito.times(2)).insert(entries.capture());
    assertEquals(new BigDecimal("1000000"), entries.getAllValues().get(0).getAmount());
    assertEquals(new BigDecimal("1000000"), entries.getAllValues().get(1).getAmount());
    verify(accountMapper).addBalance("bank", new BigDecimal("1000000"));
    verify(accountMapper).addBalance("loan", new BigDecimal("1000000"));
  }


  @Test
  void reportsDisbursementExistenceAcrossMonths() {
    when(accountMapper.selectById("loan")).thenReturn(loanAccount("loan", "JPY"));
    when(transactionMapper.selectCount(any())).thenReturn(1L, 0L);

    assertTrue(service.hasDisbursement("loan"));
    assertFalse(service.hasDisbursement("loan"));
  }

  @Test
  void rejectsAmountDifferentFromOriginalPrincipal() {
    when(accountMapper.selectById("loan")).thenReturn(loanAccount("loan", "JPY"));
    when(accountMapper.selectById("bank")).thenReturn(assetAccount("bank", "JPY"));
    when(profileMapper.selectById("loan")).thenReturn(profile("loan", "JPY", "1000000"));

    assertThrows(BusinessException.class, () -> service.create(command("900000")));
    verify(transactionMapper, never()).insert(any(Transaction.class));
  }

  @Test
  void rejectsSecondDisbursementForSameLoan() {
    when(accountMapper.selectById("loan")).thenReturn(loanAccount("loan", "JPY"));
    when(accountMapper.selectById("bank")).thenReturn(assetAccount("bank", "JPY"));
    when(profileMapper.selectById("loan")).thenReturn(profile("loan", "JPY", "1000000"));
    when(transactionMapper.selectCount(any())).thenReturn(1L);

    assertThrows(BusinessException.class, () -> service.create(command("1000000")));
    verify(transactionMapper, never()).insert(any(Transaction.class));
  }

  @Test
  void rejectsNonAssetTarget() {
    Account target = loanAccount("other", "JPY");
    when(accountMapper.selectById("loan")).thenReturn(loanAccount("loan", "JPY"));
    when(accountMapper.selectById("bank")).thenReturn(target);
    when(profileMapper.selectById("loan")).thenReturn(profile("loan", "JPY", "1000000"));

    assertThrows(BusinessException.class, () -> service.create(command("1000000")));
  }

  @Test
  void rejectsCrossCurrencyDisbursementInV1() {
    when(accountMapper.selectById("loan")).thenReturn(loanAccount("loan", "JPY"));
    when(accountMapper.selectById("bank")).thenReturn(assetAccount("bank", "USD"));
    when(profileMapper.selectById("loan")).thenReturn(profile("loan", "JPY", "1000000"));

    assertThrows(BusinessException.class, () -> service.create(command("1000000")));
  }

@Test
void removeUnpaidDisbursementRestoresBothBalances() {
  Transaction tx = new Transaction();
  tx.setTransactionId("tx");
  tx.setTransactionType("LOAN_DISBURSEMENT");
  tx.setOriginCode("LOAN_ENGINE");
  tx.setOriginReferenceId("loan");

  TransactionEntry loanEntry = new TransactionEntry();
  loanEntry.setTransactionId("tx");
  loanEntry.setAccountId("loan");
  loanEntry.setEntryRole("SOURCE");
  loanEntry.setAmount(new BigDecimal("1000"));
  loanEntry.setCurrency("JPY");

  TransactionEntry targetEntry = new TransactionEntry();
  targetEntry.setTransactionId("tx");
  targetEntry.setAccountId("bank");
  targetEntry.setEntryRole("TARGET");
  targetEntry.setAmount(new BigDecimal("1000"));
  targetEntry.setCurrency("JPY");

  when(transactionMapper.selectById("tx")).thenReturn(tx);
  when(installmentPlanMapper.selectCount(any())).thenReturn(0L);
  when(entryMapper.selectList(any())).thenReturn(List.of(loanEntry, targetEntry));
  when(accountMapper.selectById("bank")).thenReturn(assetAccount("bank", "JPY"));
  when(accountMapper.addBalance("bank", new BigDecimal("-1000"))).thenReturn(1);
  when(accountMapper.addBalance("loan", new BigDecimal("-1000"))).thenReturn(1);
  when(transactionMapper.deleteById("tx")).thenReturn(1);

  assertEquals(1, service.remove("tx"));

  verify(loanLifecycleDeletionGuard).assertLoanAccountDeletable("loan");
  verify(entryMapper).deleteByTransactionId("tx");
}

  private LoanDisbursementCreateVo command(String amount) {
    return new LoanDisbursementCreateVo(
        "loan", "bank", new BigDecimal(amount), "JPY", "initial draw", Instant.parse("2026-08-11T00:00:00Z"));
  }

  private Account loanAccount(String id, String currency) {
    Account a = new Account();
    a.setAccountId(id);
    a.setAccountClassCode("LIABILITY");
    a.setAccountTypeCode("LOAN");
    a.setCurrency(currency);
    a.setEnabled(true);
    return a;
  }

  private Account assetAccount(String id, String currency) {
    Account a = new Account();
    a.setAccountId(id);
    a.setAccountClassCode("ASSET");
    a.setAccountTypeCode("BANK");
    a.setCurrency(currency);
    a.setEnabled(true);
    return a;
  }

  private LoanProfile profile(String accountId, String currency, String principal) {
    LoanProfile p = new LoanProfile();
    p.setAccountId(accountId);
    p.setCurrency(currency);
    p.setFundingModeCode("ACCOUNT_DISBURSEMENT");
    p.setOriginalPrincipal(new BigDecimal(principal));
    return p;
  }
}
