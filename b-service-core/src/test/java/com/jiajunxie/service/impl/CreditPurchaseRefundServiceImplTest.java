package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionAllocation;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.TransactionAllocationMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.CreditCardPaymentPlanService;
import com.jiajunxie.vo.CreditPurchaseRefundRequestVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreditPurchaseRefundServiceImplTest {

  @Test
  void multiPersonRefundUsesOriginalAllocatedAmountsWhenRatiosAreNull() {
    TransactionMapper transactionMapper = mock(TransactionMapper.class);
    TransactionEntryMapper entryMapper = mock(TransactionEntryMapper.class);
    TransactionAllocationMapper allocationMapper = mock(TransactionAllocationMapper.class);
    AccountMapper accountMapper = mock(AccountMapper.class);
    CreditCardPaymentPlanService paymentPlanService = mock(CreditCardPaymentPlanService.class);

    Transaction purchase = new Transaction();
    purchase.setTransactionId("purchase");
    purchase.setTransactionType("CREDIT_PURCHASE");
    purchase.setCategoryId("food");
    purchase.setMerchant("merchant");
    when(transactionMapper.selectById("purchase")).thenReturn(purchase);

    TransactionEntry purchaseEntry = new TransactionEntry();
    purchaseEntry.setAccountId("card");
    purchaseEntry.setAmount(new BigDecimal("100"));
    purchaseEntry.setCurrency("JPY");
    when(entryMapper.selectByTransactionId("purchase")).thenReturn(List.of(purchaseEntry));

    Account card = new Account();
    card.setAccountId("card");
    card.setAccountClassCode("LIABILITY");
    card.setAccountTypeCode("CREDIT_CARD");
    card.setCurrency("JPY");
    card.setEnabled(true);
    when(accountMapper.selectById("card")).thenReturn(card);
    when(accountMapper.addBalance(anyString(), any(BigDecimal.class))).thenReturn(1);

    TransactionAllocation personA = allocation("a", "60");
    TransactionAllocation personB = allocation("b", "40");
    when(allocationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(personA, personB));

    CreditPurchaseRefundServiceImpl service =
        new CreditPurchaseRefundServiceImpl(
            transactionMapper,
            entryMapper,
            allocationMapper,
            accountMapper,
            paymentPlanService);

    service.refund(
        "purchase",
        new CreditPurchaseRefundRequestVo(
            new BigDecimal("30"),
            Instant.parse("2026-08-12T00:00:00Z"),
            "partial refund"));

    ArgumentCaptor<TransactionAllocation> captor =
        ArgumentCaptor.forClass(TransactionAllocation.class);
    verify(allocationMapper, org.mockito.Mockito.times(2)).insert(captor.capture());

    List<TransactionAllocation> inserted = captor.getAllValues();
    assertEquals(new BigDecimal("18.0000"), inserted.get(0).getAllocatedAmount());
    assertEquals(new BigDecimal("12.0000"), inserted.get(1).getAllocatedAmount());
    assertEquals(new BigDecimal("30.0000"),
        inserted.stream()
            .map(TransactionAllocation::getAllocatedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    assertNull(inserted.get(0).getAllocationRatio());
    assertNull(inserted.get(1).getAllocationRatio());

    verify(paymentPlanService)
        .applyPendingPrincipalRefund(
            org.mockito.ArgumentMatchers.eq("purchase"),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(new BigDecimal("30")));
    verify(accountMapper).addBalance("card", new BigDecimal("-30"));
  }

  @Test
  void reverseRefundRestoresPlanAndCreditCardLiability() {
    TransactionMapper transactionMapper = mock(TransactionMapper.class);
    TransactionEntryMapper entryMapper = mock(TransactionEntryMapper.class);
    TransactionAllocationMapper allocationMapper = mock(TransactionAllocationMapper.class);
    AccountMapper accountMapper = mock(AccountMapper.class);
    CreditCardPaymentPlanService paymentPlanService = mock(CreditCardPaymentPlanService.class);

    Transaction refund = new Transaction();
    refund.setTransactionId("refund");
    refund.setTransactionType("CREDIT_REFUND");
    refund.setOriginCode("CREDIT_REFUND");
    refund.setOriginReferenceId("purchase");
    when(transactionMapper.selectById("refund")).thenReturn(refund);

    TransactionEntry refundEntry = new TransactionEntry();
    refundEntry.setAccountId("card");
    refundEntry.setAmount(new BigDecimal("-30"));
    refundEntry.setCurrency("JPY");
    when(entryMapper.selectByTransactionId("refund")).thenReturn(List.of(refundEntry));

    Account card = new Account();
    card.setAccountId("card");
    card.setAccountClassCode("LIABILITY");
    card.setAccountTypeCode("CREDIT_CARD");
    when(accountMapper.selectById("card")).thenReturn(card);
    when(accountMapper.addBalance("card", new BigDecimal("30"))).thenReturn(1);
    when(transactionMapper.deleteById("refund")).thenReturn(1);

    CreditPurchaseRefundServiceImpl service =
        new CreditPurchaseRefundServiceImpl(
            transactionMapper, entryMapper, allocationMapper, accountMapper, paymentPlanService);

    service.reverse("refund");

    verify(paymentPlanService)
        .reversePendingPrincipalRefund("purchase", "refund", new BigDecimal("30"));
    verify(accountMapper).addBalance("card", new BigDecimal("30"));
    verify(allocationMapper).deleteByTransactionId("refund");
    verify(entryMapper).deleteByTransactionId("refund");
    verify(transactionMapper).deleteById("refund");
  }

  private TransactionAllocation allocation(String personId, String amount) {
    TransactionAllocation allocation = new TransactionAllocation();
    allocation.setPersonId(personId);
    allocation.setAllocatedAmount(new BigDecimal(amount));
    allocation.setCurrency("JPY");
    allocation.setAllocationRatio(null);
    return allocation;
  }
}
