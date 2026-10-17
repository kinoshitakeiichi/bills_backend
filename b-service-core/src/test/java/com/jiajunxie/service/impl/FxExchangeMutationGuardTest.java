package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.convert.TransactionConvertServiceMapper;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.mapper.PersonMapper;
import com.jiajunxie.mapper.TransactionAllocationMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.mapper.LoanPaymentDetailMapper;
import com.jiajunxie.service.CreditCardPaymentPlanService;
import com.jiajunxie.service.StatementPaymentAllocationService;
import org.junit.jupiter.api.Test;

class FxExchangeMutationGuardTest {

  @Test
  void exchangeCannotBeDeletedThroughGenericTransactionService() {
    TransactionMapper transactionMapper = mock(TransactionMapper.class);
    TransactionEntryMapper entryMapper = mock(TransactionEntryMapper.class);
    AccountMapper accountMapper = mock(AccountMapper.class);

    Transaction exchange = new Transaction();
    exchange.setTransactionId("fx");
    exchange.setTransactionType("EXCHANGE");
    when(transactionMapper.selectById("fx")).thenReturn(exchange);

    TransactionServiceImpl service =
        new TransactionServiceImpl(
            transactionMapper,
            entryMapper,
            accountMapper,
            mock(TransactionConvertServiceMapper.class),
            mock(TransactionAllocationMapper.class),
            mock(PersonMapper.class),
            mock(CategoryMapper.class),
            mock(CreditCardPaymentPlanService.class),
            mock(StatementPaymentAllocationService.class),
            mock(LoanPaymentDetailMapper.class), new TransactionMutationPolicy(), org.mockito.Mockito.mock(ForeignCurrencyPositionEngine.class));

    assertThrows(BusinessException.class, () -> service.removeTransaction("fx"));
  }
}
