package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.entity.CreditCardStatement;
import com.jiajunxie.entity.StatementPaymentAllocation;
import com.jiajunxie.mapper.CreditCardStatementMapper;
import com.jiajunxie.mapper.StatementPaymentAllocationMapper;
import com.jiajunxie.service.StatementAdjustmentService;
import com.jiajunxie.vo.StatementAdjustmentSummaryVo;
import com.jiajunxie.vo.StatementPaymentAllocationCommandVo;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StatementPaymentAllocationServiceImplTest {

  @Test
  void partialPaymentUpdatesPaidAmountAndStatus() {
    Fixture fixture = fixture();
    CreditCardStatement statement = statement("CONFIRMED", "100000", "0");

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);
    when(fixture.allocationMapper.selectList(any(Wrapper.class)))
        .thenReturn(
            List.of(),
            List.of(allocation("40000")),
            List.of(allocation("40000")));

    fixture.service.replaceAllocations(
        "payment",
        "card",
        new BigDecimal("40000"),
        "JPY",
        List.of(
            new StatementPaymentAllocationCommandVo(
                "statement", new BigDecimal("40000"), 1)));

    assertEquals(new BigDecimal("40000"), statement.getPaidAmount());
    assertEquals("PARTIALLY_PAID", statement.getStatusCode());
  }

  @Test
  void fullPaymentMarksStatementPaid() {
    Fixture fixture = fixture();
    CreditCardStatement statement = statement("CONFIRMED", "100000", "0");

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);
    when(fixture.allocationMapper.selectList(any(Wrapper.class)))
        .thenReturn(
            List.of(),
            List.of(allocation("100000")),
            List.of(allocation("100000")));

    fixture.service.replaceAllocations(
        "payment",
        "card",
        new BigDecimal("100000"),
        "JPY",
        List.of(
            new StatementPaymentAllocationCommandVo(
                "statement", new BigDecimal("100000"), 1)));

    assertEquals(new BigDecimal("100000"), statement.getPaidAmount());
    assertEquals("PAID", statement.getStatusCode());
  }

  @Test
  void partialPaymentNeverChangesStatementTotal() {
    Fixture fixture = fixture();
    CreditCardStatement statement = statement("CONFIRMED", "5000", "0");

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);
    when(fixture.allocationMapper.selectList(any(Wrapper.class)))
        .thenReturn(
            List.of(),
            List.of(allocation("2000")),
            List.of(allocation("2000")));

    fixture.service.replaceAllocations(
        "payment",
        "card",
        new BigDecimal("2000"),
        "JPY",
        List.of(
            new StatementPaymentAllocationCommandVo(
                "statement", new BigDecimal("2000"), 1)));

    assertEquals(new BigDecimal("5000"), statement.getTotalAmount());
    assertEquals(new BigDecimal("2000"), statement.getPaidAmount());
    assertEquals("PARTIALLY_PAID", statement.getStatusCode());
  }

  @Test
  void fullPaymentNeverChangesStatementTotal() {
    Fixture fixture = fixture();
    CreditCardStatement statement = statement("CONFIRMED", "5000", "0");

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);
    when(fixture.allocationMapper.selectList(any(Wrapper.class)))
        .thenReturn(
            List.of(),
            List.of(allocation("5000")),
            List.of(allocation("5000")));

    fixture.service.replaceAllocations(
        "payment",
        "card",
        new BigDecimal("5000"),
        "JPY",
        List.of(
            new StatementPaymentAllocationCommandVo(
                "statement", new BigDecimal("5000"), 1)));

    assertEquals(new BigDecimal("5000"), statement.getTotalAmount());
    assertEquals(new BigDecimal("5000"), statement.getPaidAmount());
    assertEquals("PAID", statement.getStatusCode());
  }

  @Test
  void paymentAfterPositiveAdjustmentUsesAdjustedTotal() {
    Fixture fixture = fixture();
    CreditCardStatement statement =
        statement("PARTIALLY_PAID", "5000", "5000");

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);
    when(fixture.adjustmentService.summarize("statement"))
        .thenReturn(
            new StatementAdjustmentSummaryVo(
                new BigDecimal("500"),
                new BigDecimal("5500"),
                new BigDecimal("500"),
                null,
                List.of()));
    when(fixture.allocationMapper.selectList(any(Wrapper.class)))
        .thenReturn(
            List.of(),
            List.of(allocation("5500")),
            List.of(allocation("5500")));

    fixture.service.replaceAllocations(
        "payment",
        "card",
        new BigDecimal("500"),
        "JPY",
        List.of(
            new StatementPaymentAllocationCommandVo(
                "statement", new BigDecimal("500"), 1)));

    assertEquals(new BigDecimal("5500"), statement.getPaidAmount());
    assertEquals("PAID", statement.getStatusCode());
    assertEquals(new BigDecimal("5000"), statement.getTotalAmount());
  }

  @Test
  void rejectsPaymentBeyondAdjustedOutstanding() {
    Fixture fixture = fixture();
    CreditCardStatement statement =
        statement("PARTIALLY_PAID", "5000", "5000");

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);
    when(fixture.adjustmentService.summarize("statement"))
        .thenReturn(
            new StatementAdjustmentSummaryVo(
                new BigDecimal("500"),
                new BigDecimal("5500"),
                new BigDecimal("500"),
                null,
                List.of()));
    when(fixture.allocationMapper.selectList(any(Wrapper.class)))
        .thenReturn(List.of());

    assertThrows(
        BusinessException.class,
        () ->
            fixture.service.replaceAllocations(
                "payment",
                "card",
                new BigDecimal("600"),
                "JPY",
                List.of(
                    new StatementPaymentAllocationCommandVo(
                        "statement", new BigDecimal("600"), 1))));
  }

  @Test
  void rejectsAllocationGreaterThanOutstanding() {
    Fixture fixture = fixture();
    CreditCardStatement statement =
        statement("PARTIALLY_PAID", "100000", "60000");

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);
    when(fixture.allocationMapper.selectList(any(Wrapper.class)))
        .thenReturn(List.of());

    assertThrows(
        BusinessException.class,
        () ->
            fixture.service.replaceAllocations(
                "payment",
                "card",
                new BigDecimal("50000"),
                "JPY",
                List.of(
                    new StatementPaymentAllocationCommandVo(
                        "statement", new BigDecimal("50000"), 1))));
  }

  @Test
  void rejectsAllocationSumMismatch() {
    Fixture fixture = fixture();

    assertThrows(
        BusinessException.class,
        () ->
            fixture.service.replaceAllocations(
                "payment",
                "card",
                new BigDecimal("100000"),
                "JPY",
                List.of(
                    new StatementPaymentAllocationCommandVo(
                        "statement", new BigDecimal("90000"), 1))));
  }

  @Test
  void rejectsDuplicateStatement() {
    Fixture fixture = fixture();

    assertThrows(
        BusinessException.class,
        () ->
            fixture.service.replaceAllocations(
                "payment",
                "card",
                new BigDecimal("100000"),
                "JPY",
                List.of(
                    new StatementPaymentAllocationCommandVo(
                        "statement", new BigDecimal("50000"), 1),
                    new StatementPaymentAllocationCommandVo(
                        "statement", new BigDecimal("50000"), 2))));
  }

  @Test
  void rejectsStatementOwnedByAnotherCreditCardBeforeMutatingAllocations() {
    Fixture fixture = fixture();
    CreditCardStatement statement = statement("CONFIRMED", "100000", "0");
    statement.setCreditAccountId("other-card");

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);

    assertThrows(
        BusinessException.class,
        () ->
            fixture.service.replaceAllocations(
                "payment",
                "card",
                new BigDecimal("10000"),
                "JPY",
                List.of(
                    new StatementPaymentAllocationCommandVo(
                        "statement", new BigDecimal("10000"), 1))));

    verify(fixture.allocationMapper, org.mockito.Mockito.never())
        .delete(any(Wrapper.class));
    verify(fixture.allocationMapper, org.mockito.Mockito.never())
        .insert(any(StatementPaymentAllocation.class));
  }

  @Test
  void movingPaymentBetweenStatementsRecalculatesBothHistoricalStatements() {
    Fixture fixture = fixture();

    CreditCardStatement statementA = statement("PAID", "40000", "40000");
    statementA.setStatementId("statement-a");
    CreditCardStatement statementB = statement("CONFIRMED", "100000", "0");
    statementB.setStatementId("statement-b");

    StatementPaymentAllocation old = allocation("40000");
    old.setStatementId("statement-a");
    StatementPaymentAllocation replacement = allocation("40000");
    replacement.setStatementId("statement-b");

    when(fixture.statementMapper.selectById("statement-a")).thenReturn(statementA);
    when(fixture.statementMapper.selectById("statement-b")).thenReturn(statementB);

    // replaceAllocations() reads: old payment allocations; A after removal; B after insert;
    // then A and B once more in the final reconciliation pass.
    when(fixture.allocationMapper.selectList(any(Wrapper.class)))
        .thenReturn(
            List.of(old),
            List.of(),
            List.of(replacement),
            List.of(),
            List.of(replacement));

    fixture.service.replaceAllocations(
        "payment",
        "card",
        new BigDecimal("40000"),
        "JPY",
        List.of(
            new StatementPaymentAllocationCommandVo(
                "statement-b", new BigDecimal("40000"), 1)));

    assertEquals(BigDecimal.ZERO, statementA.getPaidAmount());
    assertEquals("CONFIRMED", statementA.getStatusCode());
    assertEquals(new BigDecimal("40000"), statementB.getPaidAmount());
    assertEquals("PARTIALLY_PAID", statementB.getStatusCode());
    verify(fixture.statementMapper, atLeastOnce()).updateById(statementA);
    verify(fixture.statementMapper, atLeastOnce()).updateById(statementB);
  }

  @Test
  void deletePaymentRecalculatesStatementBackToConfirmed() {
    Fixture fixture = fixture();
    CreditCardStatement statement =
        statement("PARTIALLY_PAID", "100000", "40000");
    StatementPaymentAllocation old = allocation("40000");

    when(fixture.allocationMapper.selectList(any(Wrapper.class)))
        .thenReturn(List.of(old), List.of());
    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);

    fixture.service.deleteByPaymentTransactionId("payment");

    assertEquals(BigDecimal.ZERO, statement.getPaidAmount());
    assertEquals("CONFIRMED", statement.getStatusCode());
    verify(fixture.statementMapper).updateById(statement);
  }

  private Fixture fixture() {
    StatementPaymentAllocationMapper allocationMapper =
        mock(StatementPaymentAllocationMapper.class);
    CreditCardStatementMapper statementMapper =
        mock(CreditCardStatementMapper.class);
    StatementAdjustmentService adjustmentService =
        mock(StatementAdjustmentService.class);

    when(statementMapper.selectByIdForUpdate(any(String.class)))
        .thenAnswer(invocation -> statementMapper.selectById(invocation.getArgument(0)));

    when(adjustmentService.summarize(any(String.class)))
        .thenAnswer(
            invocation -> {
              String statementId = invocation.getArgument(0);
              CreditCardStatement statement = statementMapper.selectById(statementId);
              BigDecimal total =
                  statement == null || statement.getTotalAmount() == null
                      ? BigDecimal.ZERO
                      : statement.getTotalAmount();
              BigDecimal paid =
                  statement == null || statement.getPaidAmount() == null
                      ? BigDecimal.ZERO
                      : statement.getPaidAmount();
              return new StatementAdjustmentSummaryVo(
                  BigDecimal.ZERO,
                  total,
                  total.subtract(paid),
                  null,
                  List.of());
            });

    return new Fixture(
        new StatementPaymentAllocationServiceImpl(
            allocationMapper, statementMapper, adjustmentService),
        allocationMapper,
        statementMapper,
        adjustmentService);
  }

  private CreditCardStatement statement(
      String status, String total, String paid) {
    CreditCardStatement statement = new CreditCardStatement();
    statement.setStatementId("statement");
    statement.setCreditAccountId("card");
    statement.setTotalAmount(new BigDecimal(total));
    statement.setPaidAmount(new BigDecimal(paid));
    statement.setCurrency("JPY");
    statement.setStatusCode(status);
    return statement;
  }

  private StatementPaymentAllocation allocation(String amount) {
    StatementPaymentAllocation allocation = new StatementPaymentAllocation();
    allocation.setPaymentAllocationId("allocation");
    allocation.setPaymentTransactionId("payment");
    allocation.setStatementId("statement");
    allocation.setAllocatedAmount(new BigDecimal(amount));
    allocation.setCurrency("JPY");
    allocation.setAllocationOrder(1);
    return allocation;
  }

  private record Fixture(
      StatementPaymentAllocationServiceImpl service,
      StatementPaymentAllocationMapper allocationMapper,
      CreditCardStatementMapper statementMapper,
      StatementAdjustmentService adjustmentService) {}
}
