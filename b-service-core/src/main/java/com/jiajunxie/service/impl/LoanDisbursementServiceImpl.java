package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.LoanProfile;
import com.jiajunxie.entity.InstallmentPlan;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.LoanProfileMapper;
import com.jiajunxie.mapper.InstallmentPlanMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.LoanDisbursementService;
import com.jiajunxie.vo.LoanDisbursementCreateVo;
import com.jiajunxie.vo.LoanDisbursementCreatedVo;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanDisbursementServiceImpl implements LoanDisbursementService {
  private static final String TYPE = "LOAN_DISBURSEMENT";
  private static final String ORIGIN = "LOAN_ENGINE";

  private final TransactionMapper transactionMapper;
  private final TransactionEntryMapper transactionEntryMapper;
  private final AccountMapper accountMapper;
  private final LoanProfileMapper loanProfileMapper;
  private final InstallmentPlanMapper installmentPlanMapper;
  private final LoanLifecycleDeletionGuard loanLifecycleDeletionGuard;
  private final ForeignCurrencyPositionEngine foreignCurrencyPositionEngine;

  @Override
  @Transactional
  public LoanDisbursementCreatedVo create(LoanDisbursementCreateVo command) {
    validateShape(command);
    Account loan = requireAccount(command.loanAccountId(), "贷款账户不存在");
    Account target = requireAccount(command.targetAccountId(), "入账账户不存在");
    LoanProfile profile = loanProfileMapper.selectById(loan.getAccountId());
    if (profile == null) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款账户尚未配置 Loan Profile");
    }

    if (!"ACCOUNT_DISBURSEMENT".equals(normalize(profile.getFundingModeCode()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "当前贷款不是账户放款模式，不能登记银行/资产账户放款");
    }
    validateAccounts(command, loan, target, profile);
    assertNoExistingDisbursement(loan.getAccountId());
    lockAccountRows(loan.getAccountId(), target.getAccountId());

    String transactionId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    Instant transactionTime = command.transactionTime() == null ? now : command.transactionTime();

    Transaction transaction = new Transaction();
    transaction.setTransactionId(transactionId);
    transaction.setTransactionType(TYPE);
    transaction.setRemark(trimToNull(command.remark()));
    transaction.setTransactionTime(transactionTime);
    transaction.setOriginCode(ORIGIN);
    transaction.setOriginReferenceId(loan.getAccountId());
    transaction.setCreatedAt(now);
    transaction.setUpdatedAt(now);
    transactionMapper.insert(transaction);

    TransactionEntry targetPosted =
        postEntry(transactionId, target.getAccountId(), command.amount(), command.currency(), "TARGET", now);
    postEntry(transactionId, loan.getAccountId(), command.amount(), command.currency(), "SOURCE", now);
    applyBalance(target.getAccountId(), command.amount());
    applyBalance(loan.getAccountId(), command.amount());
    foreignCurrencyPositionEngine.apply(
        transactionId, TYPE, transactionTime, java.util.List.of(targetPosted));

    return new LoanDisbursementCreatedVo(
        transactionId, loan.getAccountId(), target.getAccountId(), command.amount(), normalize(command.currency()));
  }


  @Override
  @Transactional
  public LoanDisbursementCreatedVo update(String transactionId, LoanDisbursementCreateVo command) {
    requireText(transactionId, "贷款放款交易不能为空");
    validateShape(command);

    Transaction transaction = transactionMapper.selectById(transactionId);
    if (transaction == null || !TYPE.equals(normalize(transaction.getTransactionType()))) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "贷款放款交易不存在");
    }

    var entries = transactionEntryMapper.selectList(
        new LambdaQueryWrapper<TransactionEntry>()
            .eq(TransactionEntry::getTransactionId, transactionId));
    TransactionEntry loanEntry = entries.stream()
        .filter(entry -> "SOURCE".equals(normalize(entry.getEntryRole())))
        .findFirst()
        .orElseThrow(() -> new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款放款缺少贷款账户分录"));
    TransactionEntry targetEntry = entries.stream()
        .filter(entry -> "TARGET".equals(normalize(entry.getEntryRole())))
        .findFirst()
        .orElseThrow(() -> new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款放款缺少入账账户分录"));

    if (!loanEntry.getAccountId().equals(command.loanAccountId())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款放款不能改到其他贷款账户");
    }

    Account loan = requireAccount(command.loanAccountId(), "贷款账户不存在");
    Account oldTarget = requireAccount(targetEntry.getAccountId(), "原入账账户不存在");
    Account newTarget = requireAccount(command.targetAccountId(), "入账账户不存在");
    LoanProfile profile = loanProfileMapper.selectById(loan.getAccountId());
    if (profile == null) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款账户尚未配置 Loan Profile");
    }
    validateAccounts(command, loan, newTarget, profile);

    if (loanEntry.getAmount() == null || loanEntry.getAmount().compareTo(command.amount()) != 0
        || targetEntry.getAmount() == null || targetEntry.getAmount().compareTo(command.amount()) != 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款放款金额与现有分录不一致，不能通过编辑修改本金");
    }
    lockAccountRows(loan.getAccountId(), oldTarget.getAccountId(), newTarget.getAccountId());

    boolean targetChanged = !oldTarget.getAccountId().equals(newTarget.getAccountId());
    boolean timeChanged = command.transactionTime() != null
        && !command.transactionTime().equals(transaction.getTransactionTime());
    boolean fxRelevant = targetChanged || timeChanged;
    if (fxRelevant) {
      foreignCurrencyPositionEngine.assertLegacyMutationSafe(transactionId, entries);
      foreignCurrencyPositionEngine.reverse(transactionId);
    }

    if (targetChanged) {
      applyBalance(oldTarget.getAccountId(), command.amount().negate());
      applyBalance(newTarget.getAccountId(), command.amount());
      targetEntry.setAccountId(newTarget.getAccountId());
      if (transactionEntryMapper.updateById(targetEntry) != 1) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款放款入账账户分录更新失败");
      }
    }

    transaction.setRemark(trimToNull(command.remark()));
    if (command.transactionTime() != null) transaction.setTransactionTime(command.transactionTime());
    transaction.setUpdatedAt(Instant.now());
    if (transactionMapper.updateById(transaction) != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款放款交易更新失败");
    }
    if (fxRelevant) {
      foreignCurrencyPositionEngine.apply(
          transactionId, TYPE, transaction.getTransactionTime(), java.util.List.of(targetEntry));
    }

    return new LoanDisbursementCreatedVo(
        transactionId, loan.getAccountId(), newTarget.getAccountId(), command.amount(), normalize(command.currency()));
  }

  @Override
  public boolean hasDisbursement(String loanAccountId) {
    requireText(loanAccountId, "贷款账户不能为空");
    Account loan = requireAccount(loanAccountId, "贷款账户不存在");
    String loanType = normalize(loan.getAccountTypeCode());
    if (!"LIABILITY".equals(normalize(loan.getAccountClassCode()))
        || !(loanType.equals("LOAN") || loanType.equals("MORTGAGE") || loanType.equals("CONSUMER_LOAN"))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "指定账户不是贷款负债账户");
    }
    return disbursementCount(loanAccountId) > 0;
  }

@Override
@Transactional
public int remove(String transactionId) {
  requireText(transactionId, "贷款放款交易不能为空");

  Transaction transaction = transactionMapper.selectById(transactionId);
  if (transaction == null || !TYPE.equals(normalize(transaction.getTransactionType()))) {
    throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "贷款放款交易不存在");
  }
  if (!ORIGIN.equals(normalize(transaction.getOriginCode()))) {
    throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "仅 Loan Engine 生成的贷款放款允许撤销");
  }

  String loanAccountId = transaction.getOriginReferenceId();
  requireText(loanAccountId, "贷款放款缺少贷款账户引用");
  loanLifecycleDeletionGuard.assertLoanAccountDeletable(loanAccountId);

  Long planCount =
      installmentPlanMapper.selectCount(
          new LambdaQueryWrapper<InstallmentPlan>()
              .eq(InstallmentPlan::getSourceTransactionId, transactionId));
  if (planCount != null && planCount > 0) {
    throw new BusinessException(
        CommonErrorCode.DATA_CONFLICT, "该贷款放款已经生成还款计划，请先删除未还款计划");
  }

  var entries =
      transactionEntryMapper.selectList(
          new LambdaQueryWrapper<TransactionEntry>()
              .eq(TransactionEntry::getTransactionId, transactionId));
  if (entries.size() != 2) {
    throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款放款分录结构异常，不能安全撤销");
  }

  TransactionEntry loanEntry =
      entries.stream()
          .filter(entry -> "SOURCE".equals(normalize(entry.getEntryRole())))
          .findFirst()
          .orElseThrow(
              () -> new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款放款缺少贷款账户分录"));
  TransactionEntry targetEntry =
      entries.stream()
          .filter(entry -> "TARGET".equals(normalize(entry.getEntryRole())))
          .findFirst()
          .orElseThrow(
              () -> new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款放款缺少入账账户分录"));

  if (!loanAccountId.equals(loanEntry.getAccountId())
      || loanEntry.getAmount() == null
      || targetEntry.getAmount() == null
      || loanEntry.getAmount().compareTo(targetEntry.getAmount()) != 0
      || !normalize(loanEntry.getCurrency()).equals(normalize(targetEntry.getCurrency()))) {
    throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款放款分录不一致，不能安全撤销");
  }

  Account targetAccount = requireAccount(targetEntry.getAccountId(), "贷款放款入账账户不存在");
  lockAccountRows(loanEntry.getAccountId(), targetAccount.getAccountId());
  foreignCurrencyPositionEngine.assertLegacyMutationSafe(transactionId, entries);
  foreignCurrencyPositionEngine.reverse(transactionId);
  applyBalance(targetEntry.getAccountId(), targetEntry.getAmount().negate());
  applyBalance(loanEntry.getAccountId(), loanEntry.getAmount().negate());
  transactionEntryMapper.deleteByTransactionId(transactionId);
  return transactionMapper.deleteById(transactionId);
}

  private void validateShape(LoanDisbursementCreateVo command) {
    if (command == null) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "放款请求不能为空");
    requireText(command.loanAccountId(), "贷款账户不能为空");
    requireText(command.targetAccountId(), "入账账户不能为空");
    requireText(command.currency(), "币种不能为空");
    if (command.loanAccountId().equals(command.targetAccountId())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "贷款账户与入账账户不能相同");
    }
    if (command.amount() == null || command.amount().signum() <= 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "放款金额必须大于 0");
    }
  }

  private void validateAccounts(
      LoanDisbursementCreateVo command, Account loan, Account target, LoanProfile profile) {
    if (!Boolean.TRUE.equals(loan.getEnabled()) || !Boolean.TRUE.equals(target.getEnabled())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "放款只能使用启用账户");
    }
    String loanType = normalize(loan.getAccountTypeCode());
    if (!"LIABILITY".equals(normalize(loan.getAccountClassCode()))
        || !(loanType.equals("LOAN") || loanType.equals("MORTGAGE") || loanType.equals("CONSUMER_LOAN"))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "指定账户不是贷款负债账户");
    }
    if (!"ASSET".equals(normalize(target.getAccountClassCode()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "贷款入账账户必须是资产账户");
    }
    String currency = normalize(command.currency());
    if (!currency.equals(normalize(loan.getCurrency()))
        || !currency.equals(normalize(target.getCurrency()))
        || !currency.equals(normalize(profile.getCurrency()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "放款第一版要求贷款、入账账户与 Loan Profile 币种一致");
    }
    if (profile.getOriginalPrincipal() == null
        || profile.getOriginalPrincipal().compareTo(command.amount()) != 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "第一版放款金额必须等于 Loan Profile 初始本金");
    }
  }

  private void assertNoExistingDisbursement(String loanAccountId) {
    if (disbursementCount(loanAccountId) > 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "该贷款已经登记过放款，第一版不支持分批提款");
    }
  }


  private long disbursementCount(String loanAccountId) {
    Long count = transactionMapper.selectCount(
        new LambdaQueryWrapper<Transaction>()
            .eq(Transaction::getTransactionType, TYPE)
            .eq(Transaction::getOriginReferenceId, loanAccountId));
    return count == null ? 0L : count;
  }

  private Account requireAccount(String accountId, String message) {
    Account account = accountMapper.selectById(accountId);
    if (account == null) throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, message);
    return account;
  }

  private TransactionEntry postEntry(
      String transactionId, String accountId, BigDecimal amount, String currency, String role, Instant now) {
    TransactionEntry entry = new TransactionEntry();
    entry.setEntryId(UUID.randomUUID().toString());
    entry.setTransactionId(transactionId);
    entry.setAccountId(accountId);
    entry.setAmount(amount);
    entry.setCurrency(normalize(currency));
    entry.setEntryRole(role);
    entry.setCreatedAt(now);
    transactionEntryMapper.insert(entry);
    return entry;
  }

  private void applyBalance(String accountId, BigDecimal delta) {
    if (accountMapper.addBalance(accountId, delta) != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "账户余额更新失败");
    }
  }

  private void lockAccountRows(String... accountIds) {
    java.util.Arrays.stream(accountIds)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .sorted()
        .forEach(accountMapper::lockById);
  }

  private void requireText(String value, String message) {
    if (value == null || value.isBlank()) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, message);
  }

  private String normalize(String value) {
    return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
  }

  private String trimToNull(String value) {
    if (value == null) return null;
    String result = value.trim();
    return result.isEmpty() ? null : result;
  }
}
