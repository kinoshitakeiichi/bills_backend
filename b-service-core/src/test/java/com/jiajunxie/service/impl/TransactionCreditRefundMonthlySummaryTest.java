package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jiajunxie.convert.TransactionConvertServiceMapper;
import com.jiajunxie.entity.Account;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.mapper.LoanPaymentDetailMapper;
import com.jiajunxie.mapper.PersonMapper;
import com.jiajunxie.mapper.TransactionAllocationMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.model.AccountTransactionRow;
import com.jiajunxie.service.CreditCardPaymentPlanService;
import com.jiajunxie.service.StatementPaymentAllocationService;
import com.jiajunxie.vo.AccountTransactionVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionCreditRefundMonthlySummaryTest {

  @Test
  void creditRefundReducesMonthlyNewLiability() {
    TransactionMapper transactionMapper = mock(TransactionMapper.class);
    TransactionEntryMapper entryMapper = mock(TransactionEntryMapper.class);
    AccountMapper accountMapper = mock(AccountMapper.class);
    TransactionConvertServiceMapper convertMapper = mock(TransactionConvertServiceMapper.class);

    Account card = new Account();
    card.setAccountId("card");
    card.setAccountClassCode("LIABILITY");
    card.setAccountTypeCode("CREDIT_CARD");
    card.setCurrency("JPY");
    card.setEnabled(true);
    when(accountMapper.selectById("card")).thenReturn(card);

    AccountTransactionRow purchaseRow = mock(AccountTransactionRow.class);
    when(purchaseRow.getTransactionId()).thenReturn("purchase");
    when(purchaseRow.getTransactionType()).thenReturn("CREDIT_PURCHASE");

    AccountTransactionRow refundRow = mock(AccountTransactionRow.class);
    when(refundRow.getTransactionId()).thenReturn("refund");
    when(refundRow.getTransactionType()).thenReturn("CREDIT_REFUND");

    when(transactionMapper.selectAccountTransactions(eq("card"), any(), any(), any()))
        .thenReturn(List.of(purchaseRow, refundRow));

    when(convertMapper.toAccountTransactionVos(any()))
        .thenReturn(
            List.of(
                transaction("purchase", "CREDIT_PURCHASE", "100"),
                transaction("refund", "CREDIT_REFUND", "-30")));

    TransactionServiceImpl service =
        new TransactionServiceImpl(
            transactionMapper,
            entryMapper,
            accountMapper,
            convertMapper,
            mock(TransactionAllocationMapper.class),
            mock(PersonMapper.class),
            mock(CategoryMapper.class),
            mock(CreditCardPaymentPlanService.class),
            mock(StatementPaymentAllocationService.class),
            mock(LoanPaymentDetailMapper.class), new TransactionMutationPolicy(), org.mockito.Mockito.mock(ForeignCurrencyPositionEngine.class));

    var result =
        service.listAccountTransactions(
            "card", YearMonth.of(2026, 8), null, ZoneId.of("Asia/Tokyo"));

    assertEquals(BigDecimal.ZERO, result.totalIncome());
    assertEquals(new BigDecimal("70"), result.totalExpense());
    assertEquals(new BigDecimal("70"), result.netChange());
  }

  private AccountTransactionVo transaction(String id, String type, String amount) {
    return new AccountTransactionVo(
        id,
        type,
        type,
        null,
        null,
        null,
        null,
        Instant.parse("2026-08-12T00:00:00Z"),
        new BigDecimal(amount),
        "JPY",
        "SINGLE",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
