package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.convert.TransactionConvertServiceMapper;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.mapper.PersonMapper;
import com.jiajunxie.mapper.TransactionAllocationMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.mapper.LoanPaymentDetailMapper;
import com.jiajunxie.service.CreditCardPaymentPlanService;
import com.jiajunxie.service.StatementPaymentAllocationService;
import com.jiajunxie.vo.TransactionCreateVo;
import com.jiajunxie.vo.TransactionEntryVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FxGenericTransactionGuardTest {

  @Test
  void genericTransactionApiCannotCreateExchangeDirectly() {
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

    TransactionCreateVo rawExchange =
        new TransactionCreateVo(
            null,
            "EXCHANGE",
            null,
            null,
            null,
            Instant.parse("2026-08-10T00:00:00Z"),
            List.of(
                new TransactionEntryVo("jpy", new BigDecimal("-15000"), "JPY", "SOURCE"),
                new TransactionEntryVo("usd", new BigDecimal("100"), "USD", "TARGET")),
            List.of(),
            null,
            List.of());

    assertThrows(BusinessException.class, () -> service.createTransaction(rawExchange));
    verifyNoInteractions(transactionMapper, entryMapper);
  }
}
