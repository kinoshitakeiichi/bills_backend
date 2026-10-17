package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.CreditCardStatement;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.entity.StatementAdjustment;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CreditCardStatementMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.mapper.StatementAdjustmentMapper;
import com.jiajunxie.vo.StatementAdjustmentCreateVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class StatementAdjustmentServiceImplTest {

  @Test
  void summarizesActiveAmountAdjustmentsWithoutMutatingFrozenTotal() {
    Fixture f = fixture();
    CreditCardStatement statement = statement();
    when(f.statementMapper.selectById("statement")).thenReturn(statement);
    when(f.adjustmentMapper.selectList(any(Wrapper.class)))
        .thenReturn(
            List.of(adjustment("FEE", "500", null, 1), adjustment("WAIVER", "-200", null, 2)),
            List.of(adjustment("FEE", "500", null, 1), adjustment("WAIVER", "-200", null, 2)));

    var result = f.service.summarize("statement");

    assertEquals(new BigDecimal("5000"), statement.getTotalAmount());
    assertEquals(new BigDecimal("300"), result.postAdjustmentAmount());
    assertEquals(new BigDecimal("5300"), result.adjustedTotalAmount());
    assertEquals(new BigDecimal("3300"), result.outstandingAmount());
    assertEquals(LocalDate.of(2026, 10, 27), result.effectivePaymentDate());
  }

  @Test
  void latestActivePaymentDateAdjustmentWins() {
    Fixture f = fixture();
    CreditCardStatement statement = statement();
    when(f.statementMapper.selectById("statement")).thenReturn(statement);
    when(f.adjustmentMapper.selectList(any(Wrapper.class)))
        .thenReturn(
            List.of(
                adjustment("PAYMENT_DATE", null, LocalDate.of(2026, 11, 5), 1),
                adjustment("PAYMENT_DATE", null, LocalDate.of(2026, 11, 8), 2)),
            List.of(
                adjustment("PAYMENT_DATE", null, LocalDate.of(2026, 11, 5), 1),
                adjustment("PAYMENT_DATE", null, LocalDate.of(2026, 11, 8), 2)));

    var result = f.service.summarize("statement");

    assertEquals(BigDecimal.ZERO, result.postAdjustmentAmount());
    assertEquals(LocalDate.of(2026, 11, 8), result.effectivePaymentDate());
  }

  @Test
  void paidStatementPositiveFeeBecomesPartiallyPaid() {
    Fixture f = fixture();
    CreditCardStatement statement = statement();
    statement.setPaidAmount(new BigDecimal("5000"));
    statement.setStatusCode("PAID");
    statement.setCreditAccountId("card");

    Account card = new Account();
    card.setAccountId("card");
    card.setAccountClassCode("LIABILITY");
    card.setAccountTypeCode("CREDIT_CARD");
    card.setCurrency("JPY");

    when(f.statementMapper.selectById("statement")).thenReturn(statement);
    when(f.accountMapper.selectById("card")).thenReturn(card);
    when(f.accountMapper.addBalance("card", new BigDecimal("500"))).thenReturn(1);
    when(f.adjustmentMapper.selectList(any(Wrapper.class)))
        .thenReturn(
            List.of(),
            List.of(adjustment("FEE", "500", null, 1)),
            List.of(adjustment("FEE", "500", null, 1)));

    f.service.create(
        "statement",
        new StatementAdjustmentCreateVo(
            "FEE",
            new BigDecimal("500"),
            "JPY",
            null,
            null,
            "追加手续费"));

    assertEquals(new BigDecimal("5000"), statement.getTotalAmount());
    assertEquals("PARTIALLY_PAID", statement.getStatusCode());
  }

  @Test
  void feeCreatesLinkedLedgerTransactionAndIncreasesCreditLiability() {
    Fixture f = fixture();
    CreditCardStatement statement = statement();
    statement.setCreditAccountId("card");
    statement.setPaidAmount(BigDecimal.ZERO);
    statement.setStatusCode("CONFIRMED");

    Account card = new Account();
    card.setAccountId("card");
    card.setAccountClassCode("LIABILITY");
    card.setAccountTypeCode("CREDIT_CARD");
    card.setCurrency("JPY");

    when(f.statementMapper.selectById("statement")).thenReturn(statement);
    when(f.accountMapper.selectById("card")).thenReturn(card);
    when(f.accountMapper.addBalance("card", new BigDecimal("500"))).thenReturn(1);
    when(f.adjustmentMapper.selectList(any(Wrapper.class)))
        .thenReturn(
            List.of(),
            List.of(adjustment("FEE", "500", null, 1)),
            List.of(adjustment("FEE", "500", null, 1)));

    f.service.create(
        "statement",
        new StatementAdjustmentCreateVo(
            "FEE", new BigDecimal("500"), "JPY", null, null, "追加手续费"));

    verify(f.transactionMapper).insert(any(Transaction.class));
    verify(f.transactionEntryMapper).insert(any(TransactionEntry.class));
    verify(f.accountMapper).addBalance("card", new BigDecimal("500"));
  }

  @Test
  void reverseFeeCreatesReversalTransactionAndRestoresCreditLiability() {
    Fixture f = fixture();
    CreditCardStatement statement = statement();
    statement.setCreditAccountId("card");
    statement.setPaidAmount(BigDecimal.ZERO);
    statement.setStatusCode("CONFIRMED");

    Account card = new Account();
    card.setAccountId("card");
    card.setAccountClassCode("LIABILITY");
    card.setAccountTypeCode("CREDIT_CARD");
    card.setCurrency("JPY");

    StatementAdjustment fee = adjustment("FEE", "500", null, 1);
    fee.setStatementAdjustmentId("adjustment");
    fee.setStatementId("statement");
    fee.setLinkedTransactionId("fee-tx");

    Transaction original = new Transaction();
    original.setTransactionId("fee-tx");
    original.setTransactionType("CREDIT_STATEMENT_FEE");
    original.setOriginCode("STATEMENT_ADJUSTMENT");
    original.setOriginReferenceId("adjustment");

    when(f.statementMapper.selectById("statement")).thenReturn(statement);
    when(f.adjustmentMapper.selectById("adjustment")).thenReturn(fee);
    when(f.transactionMapper.selectById("fee-tx")).thenReturn(original);
    when(f.accountMapper.selectById("card")).thenReturn(card);
    when(f.accountMapper.addBalance("card", new BigDecimal("-500"))).thenReturn(1);
    when(f.adjustmentMapper.selectList(any(Wrapper.class)))
        .thenReturn(
            List.of(fee),
            List.of(),
            List.of());

    f.service.reverse("statement", "adjustment");

    assertEquals("REVERSED", fee.getStatusCode());
    verify(f.transactionMapper).insert(any(Transaction.class));
    verify(f.transactionEntryMapper).insert(any(TransactionEntry.class));
    verify(f.accountMapper).addBalance("card", new BigDecimal("-500"));
  }

  @Test
  void reverseFeeRejectsMissingLinkedTransaction() {
    Fixture f = fixture();
    CreditCardStatement statement = statement();
    statement.setStatusCode("CONFIRMED");
    StatementAdjustment fee = adjustment("FEE", "500", null, 1);
    fee.setStatementAdjustmentId("adjustment");
    fee.setStatementId("statement");

    when(f.statementMapper.selectById("statement")).thenReturn(statement);
    when(f.adjustmentMapper.selectById("adjustment")).thenReturn(fee);
    when(f.adjustmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of(fee));

    assertThrows(
        BusinessException.class,
        () -> f.service.reverse("statement", "adjustment"));
  }

  @Test
  void negativeAdjustmentCannotReduceAdjustedTotalBelowPaid() {
    Fixture f = fixture();
    CreditCardStatement statement = statement();
    statement.setPaidAmount(new BigDecimal("5000"));
    statement.setStatusCode("PAID");

    when(f.statementMapper.selectById("statement")).thenReturn(statement);
    when(f.adjustmentMapper.selectList(any(Wrapper.class)))
        .thenReturn(List.of(), List.of());

    assertThrows(
        BusinessException.class,
        () ->
            f.service.create(
                "statement",
                new StatementAdjustmentCreateVo(
                    "WAIVER",
                    new BigDecimal("-1"),
                    "JPY",
                    null,
                    null,
                    "非法减免")));
  }

  @Test
  void stalePaymentDateCorrectionIsRejected() {
    Fixture f = fixture();
    CreditCardStatement statement = statement();
    when(f.statementMapper.selectById("statement")).thenReturn(statement);
    when(f.adjustmentMapper.selectList(any(Wrapper.class)))
        .thenReturn(List.of(), List.of());

    assertThrows(
        BusinessException.class,
        () ->
            f.service.create(
                "statement",
                new StatementAdjustmentCreateVo(
                    "PAYMENT_DATE",
                    null,
                    null,
                    LocalDate.of(2026, 10, 26),
                    LocalDate.of(2026, 11, 5),
                    "支付日修正")));
  }

  private Fixture fixture() {
    StatementAdjustmentMapper adjustmentMapper = Mockito.mock(StatementAdjustmentMapper.class);
    CreditCardStatementMapper statementMapper = Mockito.mock(CreditCardStatementMapper.class);
    TransactionMapper transactionMapper = Mockito.mock(TransactionMapper.class);
    TransactionEntryMapper transactionEntryMapper = Mockito.mock(TransactionEntryMapper.class);
    AccountMapper accountMapper = Mockito.mock(AccountMapper.class);
    return new Fixture(
        new StatementAdjustmentServiceImpl(
            adjustmentMapper, statementMapper, transactionMapper, transactionEntryMapper, accountMapper),
        adjustmentMapper,
        statementMapper,
        transactionMapper,
        transactionEntryMapper,
        accountMapper);
  }

  private CreditCardStatement statement() {
    CreditCardStatement statement = new CreditCardStatement();
    statement.setStatementId("statement");
    statement.setTotalAmount(new BigDecimal("5000"));
    statement.setPaidAmount(new BigDecimal("2000"));
    statement.setScheduledPaymentDate(LocalDate.of(2026, 10, 27));
    statement.setCalculatedPaymentDate(LocalDate.of(2026, 10, 27));
    statement.setCurrency("JPY");
    statement.setStatusCode("PARTIALLY_PAID");
    return statement;
  }

  private StatementAdjustment adjustment(
      String type, String amount, LocalDate newPaymentDate, int order) {
    StatementAdjustment item = new StatementAdjustment();
    item.setStatementAdjustmentId("adj-" + order);
    item.setStatementId("statement");
    item.setAdjustmentTypeCode(type);
    item.setAmount(amount == null ? null : new BigDecimal(amount));
    item.setCurrency(amount == null ? null : "JPY");
    item.setNewPaymentDate(newPaymentDate);
    item.setStatusCode("ACTIVE");
    item.setCreatedAt(Instant.parse("2026-08-09T00:00:0" + order + "Z"));
    item.setUpdatedAt(item.getCreatedAt());
    return item;
  }

  private record Fixture(
      StatementAdjustmentServiceImpl service,
      StatementAdjustmentMapper adjustmentMapper,
      CreditCardStatementMapper statementMapper,
      TransactionMapper transactionMapper,
      TransactionEntryMapper transactionEntryMapper,
      AccountMapper accountMapper) {}
}
