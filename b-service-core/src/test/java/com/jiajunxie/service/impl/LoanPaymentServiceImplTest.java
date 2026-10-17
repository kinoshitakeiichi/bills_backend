package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.entity.*;
import com.jiajunxie.mapper.*;
import com.jiajunxie.vo.LoanPaymentCreateVo;
import com.jiajunxie.service.LoanPaymentAllocationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.context.ApplicationEventPublisher;

class LoanPaymentServiceImplTest {
  @Mock TransactionMapper txMapper; @Mock TransactionEntryMapper entryMapper; @Mock AccountMapper accountMapper;
  @Mock LoanProfileMapper profileMapper; @Mock PaymentScheduleMapper scheduleMapper; @Mock InstallmentPlanMapper planMapper; @Mock LoanPaymentDetailMapper detailMapper; @Mock LoanPaymentAllocationService allocationService; @Mock ApplicationEventPublisher eventPublisher;
  LoanPaymentServiceImpl service;

  @BeforeEach void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new LoanPaymentServiceImpl(txMapper, entryMapper, accountMapper, profileMapper, scheduleMapper, planMapper, detailMapper, allocationService, eventPublisher, org.mockito.Mockito.mock(ForeignCurrencyPositionEngine.class));
  }

  @Test void postsBankDecreaseAndLoanPrincipalDecrease() {
    when(accountMapper.selectById("loan")).thenReturn(account("loan","LIABILITY","1000"));
    when(accountMapper.selectById("bank")).thenReturn(account("bank","ASSET","5000"));
    LoanProfile p=new LoanProfile(); p.setAccountId("loan"); p.setCurrency("JPY"); when(profileMapper.selectById("loan")).thenReturn(p);
    when(txMapper.insert(any(Transaction.class))).thenReturn(1); when(entryMapper.insert(any(TransactionEntry.class))).thenReturn(1);
    when(accountMapper.addBalance(eq("bank"), any(BigDecimal.class))).thenReturn(1);
    when(accountMapper.addBalance(eq("loan"), any(BigDecimal.class))).thenReturn(1);
    when(detailMapper.insert(any(LoanPaymentDetail.class))).thenReturn(1);

    var r=service.create(command("800","200","0"));
    assertEquals(new BigDecimal("1000"), r.totalPaymentAmount());
    verify(accountMapper).addBalance("bank", new BigDecimal("-1000"));
    verify(accountMapper).addBalance("loan", new BigDecimal("-800"));
    ArgumentCaptor<LoanPaymentDetail> d=ArgumentCaptor.forClass(LoanPaymentDetail.class);
    verify(detailMapper).insert(d.capture());
    assertEquals(new BigDecimal("200"), d.getValue().getInterestAmount());
    verify(eventPublisher).publishEvent(any(com.jiajunxie.event.LoanPaymentCommittedEvent.class));
  }

  @Test void zeroInterestIsSupported() {
    stub("1000");
    assertDoesNotThrow(() -> service.create(command("1000","0","0")));
  }

  @Test void interestOnlyPaymentDoesNotChangeLoanPrincipal() {
    stub("1000");
    service.create(command("0","100","0"));
    verify(accountMapper, never()).addBalance(eq("loan"), any(BigDecimal.class));
  }

  @Test void principalCannotExceedRemainingBalance() {
    stub("500");
    assertThrows(BusinessException.class, () -> service.create(command("600","0","0")));
    verify(txMapper, never()).insert(any(Transaction.class));
  }

  @Test void reverseRestoresBalancesAndRemovesOnlyThePaymentFact() {
    Transaction tx = new Transaction();
    tx.setTransactionId("pay");
    tx.setTransactionType("LOAN_PAYMENT");
    tx.setOriginCode("LOAN_ENGINE");
    when(txMapper.selectById("pay")).thenReturn(tx);
    when(txMapper.deleteById("pay")).thenReturn(1);

    LoanPaymentDetail detail = new LoanPaymentDetail();
    detail.setTransactionId("pay");
    detail.setLoanAccountId("loan");
    detail.setPrincipalAmount(new BigDecimal("800"));
    detail.setInterestAmount(new BigDecimal("200"));
    detail.setFeeAmount(new BigDecimal("10"));
    detail.setCurrency("JPY");
    when(detailMapper.selectById("pay")).thenReturn(detail);
    when(detailMapper.deleteById("pay")).thenReturn(1);

    TransactionEntry source = new TransactionEntry();
    source.setEntryRole("SOURCE");
    source.setAccountId("bank");
    source.setAmount(new BigDecimal("-1010"));
    source.setCurrency("JPY");
    TransactionEntry target = new TransactionEntry();
    target.setEntryRole("TARGET");
    target.setAccountId("loan");
    target.setAmount(new BigDecimal("-800"));
    target.setCurrency("JPY");
    when(entryMapper.selectByTransactionId("pay")).thenReturn(List.of(source, target));

    when(accountMapper.selectById("bank")).thenReturn(account("bank", "ASSET", "3990"));
    when(accountMapper.selectById("loan")).thenReturn(account("loan", "LIABILITY", "200"));
    when(accountMapper.addBalance(anyString(), any(BigDecimal.class))).thenReturn(1);

    assertEquals(1, service.reverse("pay"));

    verify(allocationService).reverse("pay");
    verify(accountMapper).addBalance("bank", new BigDecimal("1010"));
    verify(accountMapper).addBalance("loan", new BigDecimal("800"));
    verify(entryMapper).deleteByTransactionId("pay");
    verify(detailMapper).deleteById("pay");
    verify(txMapper).deleteById("pay");
  }

  @Test void reverseRejectsNonLoanEnginePaymentBeforeAnyMutation() {
    Transaction tx = new Transaction();
    tx.setTransactionId("pay");
    tx.setTransactionType("LOAN_PAYMENT");
    tx.setOriginCode("IMPORT");
    when(txMapper.selectById("pay")).thenReturn(tx);

    assertThrows(BusinessException.class, () -> service.reverse("pay"));

    verifyNoInteractions(allocationService);
    verify(entryMapper, never()).deleteByTransactionId(anyString());
    verify(txMapper, never()).deleteById(anyString());
  }

  private void stub(String balance) {
    when(accountMapper.selectById("loan")).thenReturn(account("loan","LIABILITY",balance));
    when(accountMapper.selectById("bank")).thenReturn(account("bank","ASSET","5000"));
    LoanProfile p=new LoanProfile(); p.setAccountId("loan"); p.setCurrency("JPY"); when(profileMapper.selectById("loan")).thenReturn(p);
    when(txMapper.insert(any(Transaction.class))).thenReturn(1); when(entryMapper.insert(any(TransactionEntry.class))).thenReturn(1);
    when(accountMapper.addBalance(anyString(), any(BigDecimal.class))).thenReturn(1);
    when(detailMapper.insert(any(LoanPaymentDetail.class))).thenReturn(1);
  }
  private Account account(String id,String cls,String balance){ Account a=new Account(); a.setAccountId(id);a.setAccountClassCode(cls);a.setAccountTypeCode(cls.equals("LIABILITY")?"LOAN":"BANK");a.setCurrency("JPY");a.setEnabled(true);a.setTotalAmount(new BigDecimal(balance));return a;}
  private LoanPaymentCreateVo command(String principal,String interest,String fee){return new LoanPaymentCreateVo("loan","bank",null,new BigDecimal(principal),new BigDecimal(interest),new BigDecimal(fee),"JPY",null,Instant.parse("2026-08-11T00:00:00Z"));}
}
