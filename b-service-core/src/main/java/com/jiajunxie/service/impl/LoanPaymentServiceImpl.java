package com.jiajunxie.service.impl;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.InstallmentPlan;
import com.jiajunxie.entity.LoanPaymentDetail;
import com.jiajunxie.entity.LoanProfile;
import com.jiajunxie.entity.PaymentSchedule;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.event.LoanPaymentCommittedEvent;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.InstallmentPlanMapper;
import com.jiajunxie.mapper.LoanPaymentDetailMapper;
import com.jiajunxie.mapper.LoanProfileMapper;
import com.jiajunxie.mapper.PaymentScheduleMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.LoanPaymentService;
import com.jiajunxie.service.LoanPaymentAllocationService;
import com.jiajunxie.vo.LoanPaymentCreateVo;
import com.jiajunxie.vo.LoanPaymentCreatedVo;
import com.jiajunxie.vo.LoanPaymentUpdateVo;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanPaymentServiceImpl implements LoanPaymentService {
  private static final String TYPE = "LOAN_PAYMENT";
  private static final String ORIGIN = "LOAN_ENGINE";

  private final TransactionMapper transactionMapper;
  private final TransactionEntryMapper entryMapper;
  private final AccountMapper accountMapper;
  private final LoanProfileMapper profileMapper;
  private final PaymentScheduleMapper scheduleMapper;
  private final InstallmentPlanMapper planMapper;
  private final LoanPaymentDetailMapper detailMapper;
  private final LoanPaymentAllocationService allocationService;
  private final ApplicationEventPublisher eventPublisher;
  private final ForeignCurrencyPositionEngine foreignCurrencyPositionEngine;

  @Override
  @Transactional
  public LoanPaymentCreatedVo create(LoanPaymentCreateVo command) {
    validateShape(command);
    Account loan = requireAccount(command.loanAccountId(), "贷款账户不存在");
    Account source = requireAccount(command.sourceAccountId(), "扣款账户不存在");
    LoanProfile profile = profileMapper.selectById(command.loanAccountId());
    if (profile == null) throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款账户尚未配置 Loan Profile");

    validateAccounts(command, loan, source, profile);
    validateTargetSchedule(command, loan);
    lockAccountRows(loan.getAccountId(), source.getAccountId());

    BigDecimal principal = command.principalAmount();
    BigDecimal interest = command.interestAmount();
    BigDecimal fee = command.feeAmount();
    BigDecimal total = principal.add(interest).add(fee);
    if (total.signum() <= 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "本次还款总金额必须大于 0");
    }
    BigDecimal currentPrincipal = loan.getTotalAmount() == null ? BigDecimal.ZERO : loan.getTotalAmount();
    if (principal.compareTo(currentPrincipal) > 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "还款本金不能超过当前剩余本金");
    }

    String txId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    Transaction tx = new Transaction();
    tx.setTransactionId(txId);
    tx.setTransactionType(TYPE);
    tx.setRemark(trimToNull(command.remark()));
    tx.setTransactionTime(command.transactionTime() == null ? now : command.transactionTime());
    tx.setOriginCode(ORIGIN);
    tx.setOriginReferenceId(loan.getAccountId());
    tx.setCreatedAt(now);
    tx.setUpdatedAt(now);
    transactionMapper.insert(tx);

    TransactionEntry sourcePosted =
        postEntry(txId, source.getAccountId(), total.negate(), command.currency(), "SOURCE", now);
    if (principal.signum() > 0) {
      postEntry(txId, loan.getAccountId(), principal.negate(), command.currency(), "TARGET", now);
    }

    if (accountMapper.addBalance(source.getAccountId(), total.negate()) != 1
        || (principal.signum() > 0 && accountMapper.addBalance(loan.getAccountId(), principal.negate()) != 1)) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款还款余额更新失败");
    }

    LoanPaymentDetail detail = new LoanPaymentDetail();
    detail.setTransactionId(txId);
    detail.setLoanAccountId(loan.getAccountId());
    detail.setPrincipalAmount(principal);
    detail.setInterestAmount(interest);
    detail.setFeeAmount(fee);
    detail.setCurrency(normalize(command.currency()));
    detail.setCreatedAt(now);
    detailMapper.insert(detail);
    foreignCurrencyPositionEngine.apply(txId, TYPE, tx.getTransactionTime(), List.of(sourcePosted));
    eventPublisher.publishEvent(new LoanPaymentCommittedEvent(txId, command.scheduleIds() == null ? List.of() : List.copyOf(command.scheduleIds())));

    return new LoanPaymentCreatedVo(txId, loan.getAccountId(), source.getAccountId(),
        principal, interest, fee, total, normalize(command.currency()));
  }

  @Override
  @Transactional
  public LoanPaymentCreatedVo update(String transactionId, LoanPaymentUpdateVo command) {
    requireText(transactionId, "贷款还款交易不能为空");
    if (command == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "修改请求不能为空");
    }
    requireText(command.sourceAccountId(), "扣款账户不能为空");

    Transaction tx = transactionMapper.selectById(transactionId);
    if (tx == null || !TYPE.equals(normalize(tx.getTransactionType()))) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "贷款还款交易不存在");
    }
    LoanPaymentDetail detail = detailMapper.selectById(transactionId);
    if (detail == null) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款还款明细不存在");
    }

    TransactionEntry sourceEntry = entryMapper.selectOne(
        Wrappers.<TransactionEntry>lambdaQuery()
            .eq(TransactionEntry::getTransactionId, transactionId)
            .eq(TransactionEntry::getEntryRole, "SOURCE")
            .last("LIMIT 1"));
    if (sourceEntry == null) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款还款缺少扣款账户分录");
    }

    Account loan = requireAccount(detail.getLoanAccountId(), "贷款账户不存在");
    Account oldSource = requireAccount(sourceEntry.getAccountId(), "原扣款账户不存在");
    Account newSource = requireAccount(command.sourceAccountId(), "扣款账户不存在");
    LoanProfile profile = profileMapper.selectById(detail.getLoanAccountId());
    if (profile == null) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款账户尚未配置 Loan Profile");
    }

    BigDecimal total = detail.getPrincipalAmount()
        .add(detail.getInterestAmount())
        .add(detail.getFeeAmount());
    var validationCommand = new LoanPaymentCreateVo(
        loan.getAccountId(), newSource.getAccountId(), List.of(),
        detail.getPrincipalAmount(), detail.getInterestAmount(), detail.getFeeAmount(),
        detail.getCurrency(), command.remark(), command.transactionTime());
    validateAccounts(validationCommand, loan, newSource, profile);
    lockAccountRows(loan.getAccountId(), oldSource.getAccountId(), newSource.getAccountId());

    boolean sourceChanged = !oldSource.getAccountId().equals(newSource.getAccountId());
    boolean timeChanged = command.transactionTime() != null && !command.transactionTime().equals(tx.getTransactionTime());
    boolean fxRelevant = sourceChanged || timeChanged;
    if (fxRelevant) {
      foreignCurrencyPositionEngine.assertLegacyMutationSafe(transactionId, List.of(sourceEntry));
      foreignCurrencyPositionEngine.reverse(transactionId);
    }

    if (sourceChanged) {
      if (accountMapper.addBalance(oldSource.getAccountId(), total) != 1
          || accountMapper.addBalance(newSource.getAccountId(), total.negate()) != 1) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款还款扣款账户余额调整失败");
      }
      sourceEntry.setAccountId(newSource.getAccountId());
      if (entryMapper.updateById(sourceEntry) != 1) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款还款扣款账户分录更新失败");
      }
    }

    tx.setRemark(trimToNull(command.remark()));
    if (command.transactionTime() != null) {
      tx.setTransactionTime(command.transactionTime());
    }
    tx.setUpdatedAt(Instant.now());
    if (transactionMapper.updateById(tx) != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款还款交易更新失败");
    }
    if (fxRelevant) {
      foreignCurrencyPositionEngine.apply(transactionId, TYPE, tx.getTransactionTime(), List.of(sourceEntry));
    }

    return new LoanPaymentCreatedVo(
        transactionId, loan.getAccountId(), newSource.getAccountId(),
        detail.getPrincipalAmount(), detail.getInterestAmount(), detail.getFeeAmount(), total,
        normalize(detail.getCurrency()));
  }

  @Override
  @Transactional
  public int reverse(String transactionId) {
    requireText(transactionId, "贷款还款交易不能为空");

    Transaction tx = transactionMapper.selectById(transactionId);
    if (tx == null || !TYPE.equals(normalize(tx.getTransactionType()))) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "贷款还款交易不存在");
    }
    if (!ORIGIN.equals(normalize(tx.getOriginCode()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "仅 Loan Engine 生成的贷款还款允许撤销");
    }

    LoanPaymentDetail detail = detailMapper.selectById(transactionId);
    if (detail == null) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款还款明细不存在，不能安全撤销");
    }

    List<TransactionEntry> entries = entryMapper.selectByTransactionId(transactionId);
    List<TransactionEntry> sourceEntries =
        entries.stream().filter(entry -> "SOURCE".equals(normalize(entry.getEntryRole()))).toList();
    List<TransactionEntry> targetEntries =
        entries.stream().filter(entry -> "TARGET".equals(normalize(entry.getEntryRole()))).toList();
    if (sourceEntries.size() != 1 || targetEntries.size() > 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款还款分录结构异常，不能安全撤销");
    }

    BigDecimal principal = detail.getPrincipalAmount();
    BigDecimal interest = detail.getInterestAmount();
    BigDecimal fee = detail.getFeeAmount();
    if (principal == null || interest == null || fee == null) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款还款金额明细不完整，不能安全撤销");
    }
    BigDecimal total = principal.add(interest).add(fee);
    TransactionEntry sourceEntry = sourceEntries.get(0);
    if (sourceEntry.getAmount() == null
        || sourceEntry.getAmount().compareTo(total.negate()) != 0
        || !normalize(detail.getCurrency()).equals(normalize(sourceEntry.getCurrency()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款还款扣款分录与还款明细不一致");
    }

    if (principal.signum() > 0) {
      if (targetEntries.size() != 1) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款本金还款缺少贷款账户分录");
      }
      TransactionEntry targetEntry = targetEntries.get(0);
      if (!detail.getLoanAccountId().equals(targetEntry.getAccountId())
          || targetEntry.getAmount() == null
          || targetEntry.getAmount().compareTo(principal.negate()) != 0
          || !normalize(detail.getCurrency()).equals(normalize(targetEntry.getCurrency()))) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款本金分录与还款明细不一致");
      }
    } else if (!targetEntries.isEmpty()) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "非本金还款不应存在贷款本金分录");
    }

    Account source = requireAccount(sourceEntry.getAccountId(), "原贷款还款扣款账户不存在");
    Account loan = requireAccount(detail.getLoanAccountId(), "贷款账户不存在");
    lockAccountRows(loan.getAccountId(), source.getAccountId());

    foreignCurrencyPositionEngine.assertLegacyMutationSafe(transactionId, entries);
    foreignCurrencyPositionEngine.reverse(transactionId);
    allocationService.reverse(transactionId);

    if (accountMapper.addBalance(source.getAccountId(), total) != 1
        || (principal.signum() > 0 && accountMapper.addBalance(loan.getAccountId(), principal) != 1)) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款还款撤销余额恢复失败");
    }

    entryMapper.deleteByTransactionId(transactionId);
    detailMapper.deleteById(transactionId);
    return transactionMapper.deleteById(transactionId);
  }

  private void validateShape(LoanPaymentCreateVo c) {
    if (c == null) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "还款请求不能为空");
    requireText(c.loanAccountId(), "贷款账户不能为空");
    requireText(c.sourceAccountId(), "扣款账户不能为空");
    requireText(c.currency(), "币种不能为空");
    if (c.loanAccountId().equals(c.sourceAccountId())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "贷款账户与扣款账户不能相同");
    }
    if (c.principalAmount() == null || c.interestAmount() == null || c.feeAmount() == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "本金、利息和手续费不能为空");
    }
    if (c.principalAmount().signum() < 0 || c.interestAmount().signum() < 0 || c.feeAmount().signum() < 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "本金、利息和手续费不能为负数");
    }
  }

  private void validateAccounts(LoanPaymentCreateVo c, Account loan, Account source, LoanProfile profile) {
    if (!Boolean.TRUE.equals(loan.getEnabled()) || !Boolean.TRUE.equals(source.getEnabled())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "还款只能使用启用账户");
    }
    if (!"LIABILITY".equals(normalize(loan.getAccountClassCode()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "指定账户不是贷款负债账户");
    }
    if (!"ASSET".equals(normalize(source.getAccountClassCode()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "贷款还款扣款账户必须是资产账户");
    }
    String currency = normalize(c.currency());
    if (!currency.equals(normalize(loan.getCurrency()))
        || !currency.equals(normalize(source.getCurrency()))
        || !currency.equals(normalize(profile.getCurrency()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "贷款还款第一版要求贷款、扣款账户与 Profile 币种一致");
    }
  }

  private void validateTargetSchedule(LoanPaymentCreateVo command, Account loan) {
    List<String> scheduleIds = command.scheduleIds() == null ? List.of() : command.scheduleIds().stream().map(this::trimToNull).filter(java.util.Objects::nonNull).distinct().toList();
    if (scheduleIds.isEmpty()) return;
    List<PaymentSchedule> schedules = scheduleMapper.selectBatchIds(scheduleIds);
    if (schedules.size() != scheduleIds.size()) throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "部分指定贷款还款计划不存在");
    for (PaymentSchedule schedule : schedules) {
      if (!"PENDING".equals(schedule.getStatusCode()) && !"PARTIALLY_PAID".equals(schedule.getStatusCode())) throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "指定贷款还款计划当前不可还款");
      InstallmentPlan plan = planMapper.selectById(schedule.getInstallmentPlanId());
      if (plan == null || !"LOAN".equals(plan.getPaymentTypeCode()) || !loan.getAccountId().equals(plan.getLiabilityAccountId())) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "指定还款计划不属于当前贷款账户");
    }
  }

  private Account requireAccount(String id, String msg) {
    Account a = accountMapper.selectById(id);
    if (a == null) throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, msg);
    return a;
  }

  private TransactionEntry postEntry(String txId, String accountId, BigDecimal amount, String currency, String role, Instant now) {
    TransactionEntry e = new TransactionEntry();
    e.setEntryId(UUID.randomUUID().toString());
    e.setTransactionId(txId);
    e.setAccountId(accountId);
    e.setAmount(amount);
    e.setCurrency(normalize(currency));
    e.setEntryRole(role);
    e.setCreatedAt(now);
    entryMapper.insert(e);
    return e;
  }

  private void lockAccountRows(String... accountIds) {
    java.util.Arrays.stream(accountIds)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .sorted()
        .forEach(accountMapper::lockById);
  }

  private void requireText(String v, String msg) {
    if (v == null || v.isBlank()) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, msg);
  }
  private String normalize(String v) { return v == null ? null : v.trim().toUpperCase(Locale.ROOT); }
  private String trimToNull(String v) { if (v == null) return null; String x=v.trim(); return x.isEmpty()?null:x; }
}
