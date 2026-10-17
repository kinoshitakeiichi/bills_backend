package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.CreditCardStatement;
import com.jiajunxie.entity.CreditCardStatementItem;
import com.jiajunxie.entity.PaymentSchedule;
import com.jiajunxie.entity.StatementPaymentAllocation;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CreditCardStatementItemMapper;
import com.jiajunxie.mapper.CreditCardStatementMapper;
import com.jiajunxie.mapper.PaymentScheduleMapper;
import com.jiajunxie.mapper.StatementPaymentAllocationMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.CreditCardDateRuleService;
import com.jiajunxie.service.StatementAdjustmentService;
import com.jiajunxie.vo.CreditCardDatePreviewVo;
import com.jiajunxie.vo.CreditCardStatementUpdateVo;
import com.jiajunxie.vo.CreditCardStatementVo;
import com.jiajunxie.vo.StatementAdjustmentSummaryVo;
import com.jiajunxie.vo.StatementAdjustmentVo;

class CreditCardStatementServiceImplTest {

  @Test
  void updateOpenPeriodRebuildsItemsAndTotals() {
    Fixture fixture = fixture();
    CreditCardStatement statement = openStatement();
    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);
    when(fixture.statementMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
    when(fixture.accountMapper.selectById("card")).thenReturn(account());
    when(fixture.scheduleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(schedule()));
    when(fixture.itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

    fixture.service.updateOpenDates(
        "statement",
        new CreditCardStatementUpdateVo(
            LocalDate.of(2026, 8, 29),
            LocalDate.of(2026, 9, 28),
            LocalDate.of(2026, 9, 29),
            LocalDate.of(2026, 10, 27),
            LocalDate.of(2026, 10, 27),
            null));

    verify(fixture.itemMapper).delete(any(Wrapper.class));
    verify(fixture.itemMapper).insert(any(CreditCardStatementItem.class));
    assertEquals(new BigDecimal("1000"), statement.getPrincipalAmount());
    assertEquals(new BigDecimal("1000"), statement.getTotalAmount());
    assertEquals(LocalDate.of(2026, 9, 28), statement.getPeriodEnd());
  }

  @Test
  void invalidOpenPeriodIsRejected() {
    Fixture fixture = fixture();
    when(fixture.statementMapper.selectById("statement")).thenReturn(openStatement());

    assertThrows(
        BusinessException.class,
        () ->
            fixture.service.updateOpenDates(
                "statement",
                new CreditCardStatementUpdateVo(
                    LocalDate.of(2026, 9, 29),
                    LocalDate.of(2026, 9, 28),
                    LocalDate.of(2026, 9, 29),
                    LocalDate.of(2026, 10, 27),
                    LocalDate.of(2026, 10, 27),
                    null)));

    verify(fixture.statementMapper, never()).updateById(any(CreditCardStatement.class));
  }

  @Test
  void confirmFreezesItemsAndBillsSchedules() {
    Fixture fixture = fixture();
    CreditCardStatement statement = openStatement();
    CreditCardStatementItem item = item();
    PaymentSchedule schedule = schedule();

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);
    when(fixture.itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
    when(fixture.scheduleMapper.selectById("schedule")).thenReturn(schedule);

    fixture.service.confirm("statement");

    assertEquals("CONFIRMED", statement.getStatusCode());
    assertNotNull(statement.getConfirmedAt());
    assertEquals("CONFIRMED", item.getItemStatusCode());
    assertEquals("BILLED", schedule.getStatusCode());
    verify(fixture.scheduleMapper).updateById(schedule);
    verify(fixture.itemMapper).updateById(item);
    verify(fixture.statementMapper).updateById(statement);
  }

  @Test
  void confirmRejectsEmptyStatement() {
    Fixture fixture = fixture();
    when(fixture.statementMapper.selectById("statement")).thenReturn(openStatement());
    when(fixture.itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

    assertThrows(BusinessException.class, () -> fixture.service.confirm("statement"));
  }

  @Test
  void cancelOpenDeletesItemsButDoesNotTouchSchedules() {
    Fixture fixture = fixture();
    CreditCardStatement statement = openStatement();
    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);

    fixture.service.cancel("statement");

    assertEquals("CANCELLED", statement.getStatusCode());
    verify(fixture.itemMapper).delete(any(Wrapper.class));
    verify(fixture.scheduleMapper, never()).updateById(any(PaymentSchedule.class));
  }

  @Test
  void partiallyPaidStatementCannotBeRegenerated() {
    Fixture fixture = fixture();
    CreditCardStatement statement = openStatement();
    statement.setStatusCode("PARTIALLY_PAID");
    statement.setPaidAmount(new BigDecimal("200"));

    when(fixture.accountMapper.selectById("card")).thenReturn(account());
    when(fixture.statementMapper.selectOne(any(Wrapper.class))).thenReturn(statement);
    when(fixture.dateRuleService.previewSaved(any(), any()))
        .thenReturn(previewForStatement(statement));

    assertThrows(
        BusinessException.class, () -> fixture.service.generate("card", LocalDate.of(2026, 9, 1)));

    verify(fixture.itemMapper, never()).delete(any(Wrapper.class));
  }

  @Test
  void paidStatementCannotBeRegenerated() {
    Fixture fixture = fixture();
    CreditCardStatement statement = openStatement();
    statement.setStatusCode("PAID");
    statement.setPaidAmount(new BigDecimal("1000"));

    when(fixture.accountMapper.selectById("card")).thenReturn(account());
    when(fixture.statementMapper.selectOne(any(Wrapper.class))).thenReturn(statement);
    when(fixture.dateRuleService.previewSaved(any(), any()))
        .thenReturn(previewForStatement(statement));

    assertThrows(
        BusinessException.class, () -> fixture.service.generate("card", LocalDate.of(2026, 9, 1)));

    verify(fixture.itemMapper, never()).delete(any(Wrapper.class));
  }

  @Test
  void reopenConfirmedStatementRestoresItemsAndSchedules() {
    Fixture fixture = fixture();
    CreditCardStatement statement = openStatement();
    statement.setStatusCode("CONFIRMED");
    statement.setConfirmedAt(Instant.parse("2026-09-29T00:00:00Z"));

    CreditCardStatementItem item = item();
    item.setItemStatusCode("CONFIRMED");
    PaymentSchedule schedule = schedule();
    schedule.setStatusCode("BILLED");

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);
    when(fixture.itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
    when(fixture.scheduleMapper.selectById("schedule")).thenReturn(schedule);

    CreditCardStatementVo reopened = fixture.service.reopen("statement", " 确认错了，需要重新归集 ");

    assertEquals("OPEN", statement.getStatusCode());
    assertEquals(null, statement.getConfirmedAt());
    assertEquals("确认错了，需要重新归集", statement.getReopenReason());
    assertEquals(1, statement.getReopenCount());
    assertNotNull(statement.getReopenedAt());
    assertEquals("OPEN", item.getItemStatusCode());
    assertEquals("PENDING", schedule.getStatusCode());
    assertEquals("OPEN", reopened.statusCode());
    verify(fixture.scheduleMapper).updateById(schedule);
    verify(fixture.itemMapper).updateById(item);
    verify(fixture.statementMapper).updateById(statement);
  }

  @Test
  void reopenRejectsBlankReason() {
    Fixture fixture = fixture();
    CreditCardStatement statement = openStatement();
    statement.setStatusCode("CONFIRMED");
    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);

    assertThrows(BusinessException.class, () -> fixture.service.reopen("statement", "  "));

    verify(fixture.itemMapper, never()).updateById(any(CreditCardStatementItem.class));
    verify(fixture.scheduleMapper, never()).updateById(any(PaymentSchedule.class));
  }

  @Test
  void reopenRejectsNonConfirmedStatement() {
    Fixture fixture = fixture();
    when(fixture.statementMapper.selectById("statement")).thenReturn(openStatement());

    assertThrows(BusinessException.class, () -> fixture.service.reopen("statement", "重新确认"));

    verify(fixture.itemMapper, never()).updateById(any(CreditCardStatementItem.class));
  }

  @Test
  void reopenRejectsInconsistentItemState() {
    Fixture fixture = fixture();
    CreditCardStatement statement = openStatement();
    statement.setStatusCode("CONFIRMED");
    CreditCardStatementItem item = item();
    item.setItemStatusCode("OPEN");

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);
    when(fixture.itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));

    assertThrows(BusinessException.class, () -> fixture.service.reopen("statement", "状态修复"));

    verify(fixture.scheduleMapper, never()).updateById(any(PaymentSchedule.class));
  }

  @Test
  void reopenRejectsScheduleNotBilled() {
    Fixture fixture = fixture();
    CreditCardStatement statement = openStatement();
    statement.setStatusCode("CONFIRMED");
    CreditCardStatementItem item = item();
    item.setItemStatusCode("CONFIRMED");
    PaymentSchedule schedule = schedule();
    schedule.setStatusCode("PENDING");

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);
    when(fixture.itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
    when(fixture.scheduleMapper.selectById("schedule")).thenReturn(schedule);

    assertThrows(BusinessException.class, () -> fixture.service.reopen("statement", "状态修复"));

    verify(fixture.scheduleMapper, never()).updateById(any(PaymentSchedule.class));
  }

  @Test
  void reopenRejectsPaidAmountEvenIfStatusIsConfirmed() {
    Fixture fixture = fixture();
    CreditCardStatement statement = openStatement();
    statement.setStatusCode("CONFIRMED");
    statement.setPaidAmount(new BigDecimal("1"));

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);

    assertThrows(BusinessException.class, () -> fixture.service.reopen("statement", "修正确认"));

    verify(fixture.itemMapper, never()).updateById(any(CreditCardStatementItem.class));
    verify(fixture.scheduleMapper, never()).updateById(any(PaymentSchedule.class));
  }

  @Test
  void reopenRejectsExistingPaymentAllocationEvenWhenPaidAmountIsZero() {
    Fixture fixture = fixture();
    CreditCardStatement statement = openStatement();
    statement.setStatusCode("CONFIRMED");
    statement.setPaidAmount(BigDecimal.ZERO);

    StatementPaymentAllocation allocation = new StatementPaymentAllocation();
    allocation.setStatementId("statement");

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);
    when(fixture.allocationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(allocation));

    assertThrows(BusinessException.class, () -> fixture.service.reopen("statement", "修正确认"));

    verify(fixture.itemMapper, never()).updateById(any(CreditCardStatementItem.class));
  }

  @Test
  void reopenRejectsActiveStatementAdjustment() {
    Fixture fixture = fixture();
    CreditCardStatement statement = openStatement();
    statement.setStatusCode("CONFIRMED");

    StatementAdjustmentVo activeAdjustment =
        new StatementAdjustmentVo(
            "adjustment",
            "statement",
            "FEE",
            new BigDecimal("100"),
            "JPY",
            null,
            null,
            "手续费",
            "ACTIVE",
            null, // linkedTransactionId
            null, // reversalTransactionId
            null, // reversedAt
            Instant.parse("2026-09-30T00:00:00Z"),
            Instant.parse("2026-09-30T00:00:00Z"));

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);
    when(fixture.adjustmentService.summarize("statement"))
        .thenReturn(
            new StatementAdjustmentSummaryVo(
                new BigDecimal("100"),
                new BigDecimal("1100"),
                new BigDecimal("1100"),
                LocalDate.of(2026, 10, 27),
                List.of(activeAdjustment)));

    assertThrows(BusinessException.class, () -> fixture.service.reopen("statement", "修正确认"));

    verify(fixture.itemMapper, never()).updateById(any(CreditCardStatementItem.class));
  }

  @Test
  void reopenAllowsOnlyReversedAdjustments() {
    Fixture fixture = fixture();
    CreditCardStatement statement = openStatement();
    statement.setStatusCode("CONFIRMED");
    statement.setConfirmedAt(Instant.parse("2026-09-29T00:00:00Z"));
    CreditCardStatementItem item = item();
    item.setItemStatusCode("CONFIRMED");
    PaymentSchedule schedule = schedule();
    schedule.setStatusCode("BILLED");

    StatementAdjustmentVo reversedAdjustment =
        new StatementAdjustmentVo(
            "adjustment",
            "statement",
            "FEE",
            new BigDecimal("100"),
            "JPY",
            null,
            null,
            "已撤销手续费",
            "REVERSED", // linkedTransactionId
            null, // reversalTransactionId
            null, // reversedAt
            Instant.parse("2026-10-01T00:00:00Z"),
            Instant.parse("2026-09-30T00:00:00Z"),
            Instant.parse("2026-10-01T00:00:00Z"));

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);
    when(fixture.adjustmentService.summarize("statement"))
        .thenReturn(
            new StatementAdjustmentSummaryVo(
                BigDecimal.ZERO,
                new BigDecimal("1000"),
                new BigDecimal("1000"),
                LocalDate.of(2026, 10, 27),
                List.of(reversedAdjustment)));
    when(fixture.itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
    when(fixture.scheduleMapper.selectById("schedule")).thenReturn(schedule);

    fixture.service.reopen("statement", "重新归集");

    assertEquals("OPEN", statement.getStatusCode());
    assertEquals("OPEN", item.getItemStatusCode());
    assertEquals("PENDING", schedule.getStatusCode());
  }

  @Test
  void reopenValidatesAllDependenciesBeforeMutatingAnyItem() {
    Fixture fixture = fixture();
    CreditCardStatement statement = openStatement();
    statement.setStatusCode("CONFIRMED");

    CreditCardStatementItem firstItem = item();
    firstItem.setItemStatusCode("CONFIRMED");

    CreditCardStatementItem secondItem = item();
    secondItem.setStatementItemId("item-2");
    secondItem.setScheduleId("schedule-2");
    secondItem.setItemStatusCode("CONFIRMED");

    PaymentSchedule firstSchedule = schedule();
    firstSchedule.setStatusCode("BILLED");

    PaymentSchedule invalidSecondSchedule = schedule();
    invalidSecondSchedule.setScheduleId("schedule-2");
    invalidSecondSchedule.setStatusCode("PENDING");

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);
    when(fixture.itemMapper.selectList(any(Wrapper.class)))
        .thenReturn(List.of(firstItem, secondItem));
    when(fixture.scheduleMapper.selectById("schedule")).thenReturn(firstSchedule);
    when(fixture.scheduleMapper.selectById("schedule-2")).thenReturn(invalidSecondSchedule);

    assertThrows(BusinessException.class, () -> fixture.service.reopen("statement", "状态异常"));

    assertEquals("BILLED", firstSchedule.getStatusCode());
    assertEquals("CONFIRMED", firstItem.getItemStatusCode());
    verify(fixture.scheduleMapper, never()).updateById(any(PaymentSchedule.class));
    verify(fixture.itemMapper, never()).updateById(any(CreditCardStatementItem.class));
  }

  @Test
  void reopenCountIncrementsAcrossLegalConfirmReopenCycles() {
    Fixture fixture = fixture();
    CreditCardStatement statement = openStatement();
    statement.setStatusCode("CONFIRMED");
    statement.setConfirmedAt(Instant.parse("2026-09-29T00:00:00Z"));
    statement.setReopenCount(1);

    CreditCardStatementItem item = item();
    item.setItemStatusCode("CONFIRMED");
    PaymentSchedule schedule = schedule();
    schedule.setStatusCode("BILLED");

    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);
    when(fixture.itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
    when(fixture.scheduleMapper.selectById("schedule")).thenReturn(schedule);

    fixture.service.reopen("statement", "第二次重新归集");

    assertEquals(2, statement.getReopenCount());
    assertEquals("第二次重新归集", statement.getReopenReason());
  }

  @Test
  void confirmedStatementCannotBeCancelled() {
    Fixture fixture = fixture();
    CreditCardStatement statement = openStatement();
    statement.setStatusCode("CONFIRMED");
    when(fixture.statementMapper.selectById("statement")).thenReturn(statement);

    assertThrows(BusinessException.class, () -> fixture.service.cancel("statement"));
  }

  private Fixture fixture() {
    CreditCardStatementMapper statementMapper = mock(CreditCardStatementMapper.class);
    CreditCardStatementItemMapper itemMapper = mock(CreditCardStatementItemMapper.class);
    PaymentScheduleMapper scheduleMapper = mock(PaymentScheduleMapper.class);
    StatementPaymentAllocationMapper allocationMapper =
        mock(StatementPaymentAllocationMapper.class);
    TransactionMapper transactionMapper = mock(TransactionMapper.class);
    AccountMapper accountMapper = mock(AccountMapper.class);
    CreditCardDateRuleService dateRuleService = mock(CreditCardDateRuleService.class);
    StatementAdjustmentService adjustmentService = mock(StatementAdjustmentService.class);

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

              LocalDate effectivePaymentDate = null;
              if (statement != null) {
                if (statement.getManualPaymentDate() != null) {
                  effectivePaymentDate = statement.getManualPaymentDate();
                } else if (statement.getCalculatedPaymentDate() != null) {
                  effectivePaymentDate = statement.getCalculatedPaymentDate();
                } else {
                  effectivePaymentDate = statement.getScheduledPaymentDate();
                }
              }

              return new StatementAdjustmentSummaryVo(
                  BigDecimal.ZERO, total, total.subtract(paid), effectivePaymentDate, List.of());
            });

    return new Fixture(
        new CreditCardStatementServiceImpl(
            statementMapper,
            itemMapper,
            scheduleMapper,
            allocationMapper,
            transactionMapper,
            accountMapper,
            dateRuleService,
            adjustmentService),
        statementMapper,
        itemMapper,
        scheduleMapper,
        allocationMapper,
        accountMapper,
        dateRuleService,
        adjustmentService);
  }

  private CreditCardDatePreviewVo previewForStatement(CreditCardStatement statement) {
    CreditCardDatePreviewVo preview = new CreditCardDatePreviewVo();
    preview.setReferenceDate(LocalDate.of(2026, 9, 1));
    preview.setPeriodStart(statement.getPeriodStart());
    preview.setPeriodEnd(statement.getPeriodEnd());
    preview.setConfirmationDate(statement.getConfirmationDate());
    preview.setScheduledPaymentDate(statement.getScheduledPaymentDate());
    preview.setCalculatedPaymentDate(statement.getCalculatedPaymentDate());
    preview.setPaymentOptions(List.of());
    return preview;
  }

  private Account account() {
    Account account = new Account();
    account.setAccountId("card");
    account.setAccountCode("TEST_CARD");
    account.setCurrency("JPY");
    return account;
  }

  private CreditCardStatement openStatement() {
    CreditCardStatement statement = new CreditCardStatement();
    statement.setStatementId("statement");
    statement.setCreditAccountId("card");
    statement.setStatementNumber("CC-TEST-202609-001");
    statement.setPeriodStart(LocalDate.of(2026, 8, 28));
    statement.setPeriodEnd(LocalDate.of(2026, 9, 27));
    statement.setScheduledPaymentDate(LocalDate.of(2026, 10, 27));
    statement.setCalculatedPaymentDate(LocalDate.of(2026, 10, 27));
    statement.setPrincipalAmount(new BigDecimal("1000"));
    statement.setFeeAmount(BigDecimal.ZERO);
    statement.setAdjustmentAmount(BigDecimal.ZERO);
    statement.setTotalAmount(new BigDecimal("1000"));
    statement.setCurrency("JPY");
    statement.setStatusCode("OPEN");
    return statement;
  }

  private PaymentSchedule schedule() {
    PaymentSchedule schedule = new PaymentSchedule();
    schedule.setScheduleId("schedule");
    schedule.setSourceTransactionId("transaction");
    schedule.setLiabilityAccountId("card");
    schedule.setInstallmentNo(1);
    schedule.setInstallmentCount(1);
    schedule.setScheduledPrincipalAmount(new BigDecimal("1000"));
    schedule.setScheduledFeeAmount(BigDecimal.ZERO);
    schedule.setAdjustmentAmount(BigDecimal.ZERO);
    schedule.setCurrency("JPY");
    schedule.setScheduledBillingDate(LocalDate.of(2026, 9, 27));
    schedule.setStatusCode("PENDING");
    return schedule;
  }

  private CreditCardStatementItem item() {
    CreditCardStatementItem item = new CreditCardStatementItem();
    item.setStatementItemId("item");
    item.setStatementId("statement");
    item.setScheduleId("schedule");
    item.setSourceTransactionId("transaction");
    item.setInstallmentNo(1);
    item.setInstallmentCount(1);
    item.setPrincipalAmount(new BigDecimal("1000"));
    item.setFeeAmount(BigDecimal.ZERO);
    item.setAdjustmentAmount(BigDecimal.ZERO);
    item.setBilledAmount(new BigDecimal("1000"));
    item.setCurrency("JPY");
    item.setItemStatusCode("OPEN");
    return item;
  }

  private record Fixture(
      CreditCardStatementServiceImpl service,
      CreditCardStatementMapper statementMapper,
      CreditCardStatementItemMapper itemMapper,
      PaymentScheduleMapper scheduleMapper,
      StatementPaymentAllocationMapper allocationMapper,
      AccountMapper accountMapper,
      CreditCardDateRuleService dateRuleService,
      StatementAdjustmentService adjustmentService) {}
}
