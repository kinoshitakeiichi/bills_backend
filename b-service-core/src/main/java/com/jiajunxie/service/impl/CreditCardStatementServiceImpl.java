package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.CreditCardStatement;
import com.jiajunxie.entity.CreditCardStatementItem;
import com.jiajunxie.entity.PaymentSchedule;
import com.jiajunxie.entity.StatementPaymentAllocation;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CreditCardStatementItemMapper;
import com.jiajunxie.mapper.CreditCardStatementMapper;
import com.jiajunxie.mapper.PaymentScheduleMapper;
import com.jiajunxie.mapper.StatementPaymentAllocationMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.CreditCardDateRuleService;
import com.jiajunxie.service.CreditCardStatementService;
import com.jiajunxie.service.StatementAdjustmentService;
import com.jiajunxie.vo.CreditCardDatePreviewVo;
import com.jiajunxie.vo.CreditCardStatementItemVo;
import com.jiajunxie.vo.CreditCardStatementUpdateVo;
import com.jiajunxie.vo.CreditCardStatementVo;
import com.jiajunxie.vo.StatementPaymentAllocationVo;
import com.jiajunxie.vo.StatementAdjustmentSummaryVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreditCardStatementServiceImpl implements CreditCardStatementService {

  private static final String STATUS_OPEN = "OPEN";
  private static final String STATUS_CONFIRMED = "CONFIRMED";
  private static final String STATUS_PARTIALLY_PAID = "PARTIALLY_PAID";
  private static final String STATUS_PAID = "PAID";
  private static final String STATUS_CANCELLED = "CANCELLED";
  private static final String SCHEDULE_PENDING = "PENDING";
  private static final String SCHEDULE_BILLED = "BILLED";

  private final CreditCardStatementMapper statementMapper;
  private final CreditCardStatementItemMapper itemMapper;
  private final PaymentScheduleMapper scheduleMapper;
  private final StatementPaymentAllocationMapper allocationMapper;
  private final TransactionMapper transactionMapper;
  private final AccountMapper accountMapper;
  private final CreditCardDateRuleService dateRuleService;
  private final StatementAdjustmentService adjustmentService;

  @Override
  @Transactional
  public CreditCardStatementVo generate(String accountId, LocalDate referenceDate) {
    Account account = requireAccount(accountId);
    CreditCardDatePreviewVo preview = dateRuleService.previewSaved(accountId, referenceDate);

    CreditCardStatement statement =
        statementMapper.selectOne(
            new LambdaQueryWrapper<CreditCardStatement>()
                .eq(CreditCardStatement::getCreditAccountId, accountId)
                .eq(CreditCardStatement::getPeriodStart, preview.getPeriodStart())
                .eq(CreditCardStatement::getPeriodEnd, preview.getPeriodEnd())
                .last("LIMIT 1"));

    if (statement != null && isFrozenStatement(statement)) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "该账期已经确认或发生还款，不能重新生成");
    }

    Instant now = Instant.now();
    boolean fresh = statement == null;
    if (fresh) {
      statement = new CreditCardStatement();
      statement.setStatementId(UUID.randomUUID().toString());
      statement.setCreditAccountId(accountId);
      statement.setStatementNumber(
          buildNumber(account, preview.getPeriodEnd(), statement.getStatementId()));
      statement.setCreatedAt(now);
    } else {
      deleteItems(statement.getStatementId());
    }

    statement.setStatusCode(STATUS_OPEN);
    statement.setConfirmedAt(null);
    statement.setStatementYear(preview.getPeriodEnd().getYear());
    statement.setStatementMonth(preview.getPeriodEnd().getMonthValue());
    statement.setPeriodStart(preview.getPeriodStart());
    statement.setPeriodEnd(preview.getPeriodEnd());
    statement.setConfirmationDate(preview.getConfirmationDate());
    statement.setScheduledPaymentDate(preview.getScheduledPaymentDate());
    statement.setCalculatedPaymentDate(preview.getCalculatedPaymentDate());
    statement.setManualPaymentDate(null);
    statement.setCurrency(account.getCurrency());
    if (statement.getPaidAmount() == null) statement.setPaidAmount(BigDecimal.ZERO);
    if (statement.getPaidAmount().signum() != 0) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "已发生还款的账单不能重新生成");
    }
    statement.setUpdatedAt(now);

    validateNoActiveOverlap(statement);

    if (fresh) {
      statementMapper.insert(statement);
    } else {
      statementMapper.updateById(statement);
    }

    rebuildItemsAndTotals(statement, account, now);
    statementMapper.updateById(statement);
    return get(statement.getStatementId());
  }

  @Override
  public CreditCardStatementVo get(String statementId) {
    CreditCardStatement statement = requireStatement(statementId);
    List<CreditCardStatementItem> items =
        itemMapper.selectList(
            new LambdaQueryWrapper<CreditCardStatementItem>()
                .eq(CreditCardStatementItem::getStatementId, statementId)
                .orderByAsc(CreditCardStatementItem::getInstallmentNo));
    return toVo(statement, items);
  }

  @Override
  public List<CreditCardStatementVo> list(String accountId, String statusCode) {
    LambdaQueryWrapper<CreditCardStatement> query =
        new LambdaQueryWrapper<CreditCardStatement>()
            .eq(CreditCardStatement::getCreditAccountId, accountId)
            .ne(CreditCardStatement::getStatusCode, STATUS_CANCELLED)
            .orderByDesc(CreditCardStatement::getPeriodEnd);

    if (statusCode != null && !statusCode.isBlank()) {
      query.eq(
          CreditCardStatement::getStatusCode,
          statusCode.trim().toUpperCase(Locale.ROOT));
    }

    return statementMapper.selectList(query).stream()
        .map(statement -> toVo(statement, List.of()))
        .toList();
  }

  @Override
  public List<CreditCardStatementVo> listPayable(String accountId) {
    return statementMapper
        .selectList(
            new LambdaQueryWrapper<CreditCardStatement>()
                .eq(CreditCardStatement::getCreditAccountId, accountId)
                .in(
                    CreditCardStatement::getStatusCode,
                    "CONFIRMED",
                    "PARTIALLY_PAID")
                .orderByAsc(CreditCardStatement::getPeriodEnd))
        .stream()
        .filter(statement -> outstanding(statement).signum() > 0)
        .map(statement -> toVo(statement, List.of()))
        .toList();
  }

  @Override
  @Transactional
  public CreditCardStatementVo updateOpenDates(
      String statementId, CreditCardStatementUpdateVo update) {
    CreditCardStatement statement = requireOpen(statementId);
    validatePeriod(update.periodStart(), update.periodEnd());

    LocalDate oldStart = statement.getPeriodStart();
    LocalDate oldEnd = statement.getPeriodEnd();

    statement.setPeriodStart(update.periodStart());
    statement.setPeriodEnd(update.periodEnd());
    statement.setStatementYear(update.periodEnd().getYear());
    statement.setStatementMonth(update.periodEnd().getMonthValue());
    statement.setConfirmationDate(update.confirmationDate());
    statement.setScheduledPaymentDate(update.scheduledPaymentDate());
    statement.setCalculatedPaymentDate(update.calculatedPaymentDate());
    statement.setManualPaymentDate(update.manualPaymentDate());
    statement.setUpdatedAt(Instant.now());

    validateNoActiveOverlap(statement);
    statementMapper.updateById(statement);

    boolean periodChanged =
        !Objects.equals(oldStart, update.periodStart())
            || !Objects.equals(oldEnd, update.periodEnd());

    if (periodChanged) {
      Account account = requireAccount(statement.getCreditAccountId());
      deleteItems(statementId);
      rebuildItemsAndTotals(statement, account, statement.getUpdatedAt());
      statementMapper.updateById(statement);
    }

    return get(statementId);
  }

  @Override
  @Transactional
  public CreditCardStatementVo confirm(String statementId) {
    CreditCardStatement statement = requireOpen(statementId);
    List<CreditCardStatementItem> items =
        itemMapper.selectList(
            new LambdaQueryWrapper<CreditCardStatementItem>()
                .eq(CreditCardStatementItem::getStatementId, statementId));

    if (items.isEmpty()) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "账单没有可确认的明细");
    }

    verifyTotals(statement, items);
    Instant now = Instant.now();

    for (CreditCardStatementItem item : items) {
      PaymentSchedule schedule = scheduleMapper.selectById(item.getScheduleId());
      if (schedule == null) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "账单引用的付款计划不存在");
      }
      if (!SCHEDULE_PENDING.equals(schedule.getStatusCode())) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT, "付款计划不是 PENDING 状态，不能确认账单");
      }

      schedule.setStatusCode(SCHEDULE_BILLED);
      schedule.setUpdatedAt(now);
      scheduleMapper.updateById(schedule);

      item.setItemStatusCode(STATUS_CONFIRMED);
      itemMapper.updateById(item);
    }

    statement.setStatusCode(STATUS_CONFIRMED);
    statement.setConfirmedAt(now);
    statement.setUpdatedAt(now);
    statementMapper.updateById(statement);
    return get(statementId);
  }

  @Override
  @Transactional
  public CreditCardStatementVo reopen(String statementId, String reason) {
    CreditCardStatement statement = requireStatement(statementId);

    if (!STATUS_CONFIRMED.equals(statement.getStatusCode())) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "只有 CONFIRMED 状态账单允许撤销确认");
    }
    if (reason == null || reason.isBlank()) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR, "撤销确认原因不能为空");
    }

    BigDecimal paid =
        statement.getPaidAmount() == null ? BigDecimal.ZERO : statement.getPaidAmount();
    if (paid.signum() > 0) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "账单已经发生还款，不能撤销确认");
    }

    List<StatementPaymentAllocation> paymentAllocations =
        allocationMapper.selectList(
            new LambdaQueryWrapper<StatementPaymentAllocation>()
                .eq(StatementPaymentAllocation::getStatementId, statementId));
    if (!paymentAllocations.isEmpty()) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "账单存在还款分配记录，不能撤销确认");
    }

    StatementAdjustmentSummaryVo adjustmentSummary =
        adjustmentService.summarize(statementId);
    boolean hasActiveAdjustment =
        adjustmentSummary.adjustments().stream()
            .anyMatch(adjustment -> "ACTIVE".equals(adjustment.statusCode()));
    if (hasActiveAdjustment) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "账单存在有效账后调整，不能撤销确认");
    }

    List<CreditCardStatementItem> items =
        itemMapper.selectList(
            new LambdaQueryWrapper<CreditCardStatementItem>()
                .eq(CreditCardStatementItem::getStatementId, statementId));

    if (items.isEmpty()) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "账单没有可撤销确认的明细");
    }

    // Validate the entire dependency graph before changing any row. This makes an
    // inconsistent second item unable to partially mutate the first item even before
    // the transaction manager rolls the database work back.
    List<PaymentSchedule> schedules = new ArrayList<>(items.size());
    for (CreditCardStatementItem item : items) {
      if (!STATUS_CONFIRMED.equals(item.getItemStatusCode())) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT, "账单明细状态不一致，不能撤销确认");
      }

      PaymentSchedule schedule = scheduleMapper.selectById(item.getScheduleId());
      if (schedule == null) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT, "账单引用的付款计划不存在");
      }
      if (!SCHEDULE_BILLED.equals(schedule.getStatusCode())) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT, "付款计划不是 BILLED 状态，不能撤销确认");
      }
      schedules.add(schedule);
    }

    Instant now = Instant.now();
    for (int index = 0; index < items.size(); index++) {
      CreditCardStatementItem item = items.get(index);
      PaymentSchedule schedule = schedules.get(index);

      schedule.setStatusCode(SCHEDULE_PENDING);
      schedule.setUpdatedAt(now);
      scheduleMapper.updateById(schedule);

      item.setItemStatusCode(STATUS_OPEN);
      itemMapper.updateById(item);
    }

    statement.setStatusCode(STATUS_OPEN);
    statement.setConfirmedAt(null);
    statement.setReopenedAt(now);
    statement.setReopenReason(reason.trim());
    statement.setReopenCount(
        (statement.getReopenCount() == null ? 0 : statement.getReopenCount()) + 1);
    statement.setUpdatedAt(now);
    statementMapper.updateById(statement);

    return get(statementId);
  }

  @Override
  @Transactional
  public CreditCardStatementVo cancel(String statementId) {
    CreditCardStatement statement = requireOpen(statementId);

    deleteItems(statementId);
    statement.setStatusCode(STATUS_CANCELLED);
    statement.setUpdatedAt(Instant.now());
    statementMapper.updateById(statement);

    return get(statementId);
  }

  private boolean isFrozenStatement(CreditCardStatement statement) {
    String status = statement.getStatusCode();
    BigDecimal paid =
        statement.getPaidAmount() == null ? BigDecimal.ZERO : statement.getPaidAmount();

    return STATUS_CONFIRMED.equals(status)
        || STATUS_PARTIALLY_PAID.equals(status)
        || STATUS_PAID.equals(status)
        || paid.signum() > 0;
  }

  private Account requireAccount(String accountId) {
    Account account = accountMapper.selectById(accountId);
    if (account == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "信用卡账户不存在");
    }
    return account;
  }

  private CreditCardStatement requireStatement(String statementId) {
    CreditCardStatement statement = statementMapper.selectById(statementId);
    if (statement == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "信用卡账单不存在");
    }
    return statement;
  }

  private CreditCardStatement requireOpen(String statementId) {
    CreditCardStatement statement = requireStatement(statementId);
    if (!STATUS_OPEN.equals(statement.getStatusCode())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "只有 OPEN 状态账单允许执行该操作");
    }
    return statement;
  }

  private void validatePeriod(LocalDate start, LocalDate end) {
    if (start == null || end == null || start.isAfter(end)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "账期开始日不能晚于结束日");
    }
  }

  private void validateNoActiveOverlap(CreditCardStatement candidate) {
    List<CreditCardStatement> overlaps =
        statementMapper.selectList(
            new LambdaQueryWrapper<CreditCardStatement>()
                .eq(CreditCardStatement::getCreditAccountId, candidate.getCreditAccountId())
                .in(CreditCardStatement::getStatusCode, STATUS_OPEN, STATUS_CONFIRMED)
                .le(CreditCardStatement::getPeriodStart, candidate.getPeriodEnd())
                .ge(CreditCardStatement::getPeriodEnd, candidate.getPeriodStart()));

    boolean conflict =
        overlaps.stream()
            .anyMatch(
                statement ->
                    !Objects.equals(statement.getStatementId(), candidate.getStatementId()));
    if (conflict) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "该信用卡已经存在重叠的有效账单");
    }
  }

  private void rebuildItemsAndTotals(
      CreditCardStatement statement, Account account, Instant now) {
    List<PaymentSchedule> schedules =
        scheduleMapper.selectList(
            new LambdaQueryWrapper<PaymentSchedule>()
                .eq(PaymentSchedule::getLiabilityAccountId, statement.getCreditAccountId())
                .between(
                    PaymentSchedule::getScheduledBillingDate,
                    statement.getPeriodStart(),
                    statement.getPeriodEnd())
                .eq(PaymentSchedule::getStatusCode, SCHEDULE_PENDING)
                .orderByAsc(PaymentSchedule::getScheduledBillingDate)
                .orderByAsc(PaymentSchedule::getInstallmentNo));

    BigDecimal principal = BigDecimal.ZERO;
    BigDecimal fee = BigDecimal.ZERO;
    BigDecimal adjustment = BigDecimal.ZERO;

    for (PaymentSchedule schedule : schedules) {
      if (!Objects.equals(account.getCurrency(), schedule.getCurrency())) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT, "付款计划币种与信用卡账户币种不一致");
      }
      validateScheduleNotInAnotherActiveStatement(
          statement.getStatementId(), schedule.getScheduleId());

      CreditCardStatementItem item = snapshot(statement.getStatementId(), schedule, now);
      itemMapper.insert(item);

      principal = principal.add(item.getPrincipalAmount());
      fee = fee.add(item.getFeeAmount());
      adjustment = adjustment.add(item.getAdjustmentAmount());
    }

    statement.setPrincipalAmount(principal);
    statement.setFeeAmount(fee);
    statement.setAdjustmentAmount(adjustment);
    statement.setTotalAmount(principal.add(fee).add(adjustment));
  }

  private void validateScheduleNotInAnotherActiveStatement(
      String statementId, String scheduleId) {
    List<CreditCardStatementItem> references =
        itemMapper.selectList(
            new LambdaQueryWrapper<CreditCardStatementItem>()
                .eq(CreditCardStatementItem::getScheduleId, scheduleId));

    for (CreditCardStatementItem reference : references) {
      if (Objects.equals(reference.getStatementId(), statementId)) {
        continue;
      }

      CreditCardStatement owner = statementMapper.selectById(reference.getStatementId());
      if (owner != null && !STATUS_CANCELLED.equals(owner.getStatusCode())) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "同一个付款计划不能进入两张有效账单");
      }
    }
  }

  private void verifyTotals(
      CreditCardStatement statement, List<CreditCardStatementItem> items) {
    BigDecimal principal = BigDecimal.ZERO;
    BigDecimal fee = BigDecimal.ZERO;
    BigDecimal adjustment = BigDecimal.ZERO;

    for (CreditCardStatementItem item : items) {
      principal = principal.add(item.getPrincipalAmount());
      fee = fee.add(item.getFeeAmount());
      adjustment = adjustment.add(item.getAdjustmentAmount());

      BigDecimal expectedItem =
          item.getPrincipalAmount().add(item.getFeeAmount()).add(item.getAdjustmentAmount());
      if (expectedItem.compareTo(item.getBilledAmount()) != 0) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "账单明细金额校验失败");
      }
    }

    BigDecimal total = principal.add(fee).add(adjustment);
    if (principal.compareTo(statement.getPrincipalAmount()) != 0
        || fee.compareTo(statement.getFeeAmount()) != 0
        || adjustment.compareTo(statement.getAdjustmentAmount()) != 0
        || total.compareTo(statement.getTotalAmount()) != 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "账单汇总金额校验失败");
    }
  }

  private void deleteItems(String statementId) {
    itemMapper.delete(
        new LambdaQueryWrapper<CreditCardStatementItem>()
            .eq(CreditCardStatementItem::getStatementId, statementId));
  }

  private CreditCardStatementItem snapshot(
      String statementId, PaymentSchedule schedule, Instant now) {
    CreditCardStatementItem item = new CreditCardStatementItem();
    item.setStatementItemId(UUID.randomUUID().toString());
    item.setStatementId(statementId);
    item.setScheduleId(schedule.getScheduleId());
    item.setSourceTransactionId(schedule.getSourceTransactionId());
    item.setInstallmentNo(schedule.getInstallmentNo());
    item.setInstallmentCount(schedule.getInstallmentCount());
    item.setPrincipalAmount(schedule.getScheduledPrincipalAmount());
    item.setFeeAmount(schedule.getScheduledFeeAmount());
    item.setAdjustmentAmount(schedule.getAdjustmentAmount());
    item.setBilledAmount(
        schedule
            .getScheduledPrincipalAmount()
            .add(schedule.getScheduledFeeAmount())
            .add(schedule.getAdjustmentAmount()));
    item.setCurrency(schedule.getCurrency());
    item.setItemStatusCode(STATUS_OPEN);
    item.setCreatedAt(now);
    return item;
  }

  private String buildNumber(Account account, LocalDate periodEnd, String id) {
    String accountCode =
        account.getAccountCode() == null
            ? "CARD"
            : account.getAccountCode().replaceAll("[^A-Za-z0-9_-]", "_");
    return "CC-"
        + accountCode
        + "-"
        + String.format("%04d%02d", periodEnd.getYear(), periodEnd.getMonthValue())
        + "-"
        + id.substring(0, 8).toUpperCase(Locale.ROOT);
  }

  private BigDecimal outstanding(CreditCardStatement statement) {
    BigDecimal paid =
        statement.getPaidAmount() == null ? BigDecimal.ZERO : statement.getPaidAmount();
    return statement.getTotalAmount().subtract(paid);
  }

  private List<StatementPaymentAllocationVo> paymentVos(String statementId) {
    return allocationMapper
        .selectList(
            new LambdaQueryWrapper<StatementPaymentAllocation>()
                .eq(StatementPaymentAllocation::getStatementId, statementId)
                .orderByAsc(StatementPaymentAllocation::getCreatedAt)
                .orderByAsc(StatementPaymentAllocation::getAllocationOrder))
        .stream()
        .map(
            allocation ->
                new StatementPaymentAllocationVo(
                    allocation.getPaymentAllocationId(),
                    allocation.getPaymentTransactionId(),
                    allocation.getStatementId(),
                    allocation.getAllocatedAmount(),
                    allocation.getCurrency(),
                    allocation.getAllocationOrder(),
                    allocation.getCreatedAt()))
        .toList();
  }

  private CreditCardStatementVo toVo(
      CreditCardStatement statement, List<CreditCardStatementItem> items) {
    BigDecimal paid =
        statement.getPaidAmount() == null ? BigDecimal.ZERO : statement.getPaidAmount();
    StatementAdjustmentSummaryVo adjustmentSummary =
        adjustmentService.summarize(statement.getStatementId());

    Map<String, Transaction> sourceTransactions =
        items.isEmpty()
            ? Map.of()
            : transactionMapper.selectBatchIds(
                    items.stream()
                        .map(CreditCardStatementItem::getSourceTransactionId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Transaction::getTransactionId, Function.identity()));

    return new CreditCardStatementVo(
        statement.getStatementId(),
        statement.getCreditAccountId(),
        statement.getStatementNumber(),
        statement.getPeriodStart(),
        statement.getPeriodEnd(),
        statement.getConfirmationDate(),
        statement.getScheduledPaymentDate(),
        statement.getCalculatedPaymentDate(),
        statement.getManualPaymentDate(),
        statement.getPrincipalAmount(),
        statement.getFeeAmount(),
        statement.getAdjustmentAmount(),
        statement.getTotalAmount(),
        adjustmentSummary.postAdjustmentAmount(),
        adjustmentSummary.adjustedTotalAmount(),
        paid,
        adjustmentSummary.outstandingAmount(),
        adjustmentSummary.effectivePaymentDate(),
        statement.getCurrency(),
        statement.getStatusCode(),
        statement.getConfirmedAt(),
        statement.getReopenedAt(),
        statement.getReopenReason(),
        statement.getReopenCount(),
        items.stream()
            .map(
                item ->
                    new CreditCardStatementItemVo(
                        item.getStatementItemId(),
                        item.getScheduleId(),
                        item.getSourceTransactionId(),
                        sourceTransactions.containsKey(item.getSourceTransactionId())
                            ? sourceTransactions.get(item.getSourceTransactionId()).getTransactionTime()
                            : null,
                        item.getInstallmentNo(),
                        item.getInstallmentCount(),
                        item.getPrincipalAmount(),
                        item.getFeeAmount(),
                        item.getAdjustmentAmount(),
                        item.getBilledAmount(),
                        item.getCurrency(),
                        item.getItemStatusCode()))
            .toList(),
        paymentVos(statement.getStatementId()),
        adjustmentSummary.adjustments());
  }
}
