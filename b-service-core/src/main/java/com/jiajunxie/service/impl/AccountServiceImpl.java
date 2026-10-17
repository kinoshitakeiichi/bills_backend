package com.jiajunxie.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.convert.AccountConvertServiceMapper;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.LoanProfile;
import com.jiajunxie.entity.LoanRateHistory;
import com.jiajunxie.entity.Person;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.LoanProfileMapper;
import com.jiajunxie.mapper.LoanRateHistoryMapper;
import com.jiajunxie.mapper.FxPositionMapper;
import com.jiajunxie.mapper.PersonMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.service.AccountService;
import com.jiajunxie.service.AccountTypeMappingService;
import com.jiajunxie.vo.AccountIdLabelVo;
import com.jiajunxie.vo.AccountInfoVo;
import com.jiajunxie.vo.AccountOptionVo;
import com.jiajunxie.vo.AccountTypeMappingVo;
import com.jiajunxie.vo.AccountVo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * Account master-data service.
 *
 * <p>Account type is user-selected, while account class is derived by the backend. References to
 * Person, parent account and auto-debit account are validated here rather than trusted from the UI.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AccountServiceImpl implements AccountService {

  private final AccountMapper accountMapper;
  private final AccountConvertServiceMapper accountConvertMapper;
  private final PersonMapper personMapper;
  private final AccountTypeMappingService accountTypeMappingService;
  private final TransactionEntryMapper transactionEntryMapper;
  private final LoanProfileMapper loanProfileMapper;
  private final LoanRateHistoryMapper loanRateHistoryMapper;
  private final LoanLifecycleDeletionGuard loanLifecycleDeletionGuard;
  private final FxPositionMapper fxPositionMapper;

  @Override
  public Integer addAccount(AccountVo accountVo) {
    validateAccount(accountVo, null);
    Account account = accountConvertMapper.toEntity(accountVo);
    account.setAccountId(UUID.randomUUID().toString());
    account.setAccountClassCode(
        accountTypeMappingService.resolveAccountClassCode(account.getAccountTypeCode()));
    account.setTotalAmount(BigDecimal.ZERO);
    account.setIsBaseAccount(account.getParentAccountId() == null);
    account.setEnabled(true);
    Instant now = Instant.now();
    account.setCreatedAt(now);
    account.setUpdatedAt(now);
    return accountMapper.insert(account);
  }

  @Override
  public List<AccountInfoVo> cardList() {
    List<Account> accounts = accountMapper.selectList(null);
    if (accounts.isEmpty()) {
      return List.of();
    }

    /*
     * Account stores only ownerPersonId as the relationship key. Account cards are read models, so
     * enrich them with Person names here instead of making the frontend translate UUIDs. We load
     * all referenced persons in one batch to avoid an N+1 query and we intentionally include
     * disabled persons so historical accounts still keep a readable owner name.
     */
    List<String> ownerPersonIds =
        accounts.stream()
            .map(Account::getOwnerPersonId)
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();

    Map<String, Person> personsById =
        ownerPersonIds.isEmpty()
            ? Map.of()
            : personMapper.selectByIds(ownerPersonIds).stream()
                .collect(Collectors.toMap(Person::getPersonId, Function.identity()));

    Map<String, AccountTypeMappingVo> mappingsByTypeCode =
        accountTypeMappingService.listMappings().stream()
            .collect(Collectors.toMap(AccountTypeMappingVo::accountTypeCode, Function.identity()));

    return accounts.stream()
        .map(
            account -> {
              Person owner = personsById.get(account.getOwnerPersonId());
              AccountTypeMappingVo mapping = mappingsByTypeCode.get(account.getAccountTypeCode());
              String ownerPersonName = owner == null ? null : owner.getPersonName();
              String accountTypeName = mapping == null ? null : mapping.accountTypeName();
              String accountClassName = mapping == null ? null : mapping.accountClassName();
              return accountConvertMapper.toInfoVo(
                  account, ownerPersonName, accountClassName, accountTypeName);
            })
        .toList();
  }

  @Override
  public List<AccountIdLabelVo> idLabelList() {
    List<Account> accounts =
        accountMapper.selectList(
            Wrappers.<Account>lambdaQuery()
                .eq(Account::getEnabled, true)
                .orderByAsc(Account::getOwnerPersonId));
    fillOwnerPersons(accounts);
    return accountConvertMapper.toIdLabelList(accounts);
  }

  @Override
  public List<AccountOptionVo> optionList(
      String accountClassCode, String accountTypeCode, Boolean baseOnly) {
    var query = Wrappers.<Account>lambdaQuery().eq(Account::getEnabled, true);
    if (accountClassCode != null && !accountClassCode.isBlank()) {
      query.eq(Account::getAccountClassCode, accountClassCode.trim().toUpperCase(Locale.ROOT));
    }
    if (accountTypeCode != null && !accountTypeCode.isBlank()) {
      query.eq(Account::getAccountTypeCode, accountTypeCode.trim().toUpperCase(Locale.ROOT));
    }
    if (Boolean.TRUE.equals(baseOnly)) {
      query.eq(Account::getIsBaseAccount, true);
    }
    query.orderByAsc(Account::getAccountName).orderByAsc(Account::getCurrency);

    List<Account> accounts = accountMapper.selectList(query);
    fillOwnerPersons(accounts);
    return accountConvertMapper.toOptions(accounts);
  }

  @Override
  public Integer updateMasterData(AccountVo accountVo) {
    if (accountVo == null || accountVo.accountId() == null || accountVo.accountId().isBlank()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "账户 ID 不能为空");
    }
    Account current = accountMapper.selectById(accountVo.accountId());
    if (current == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "账户不存在");
    }
    assertFinancialSemanticsMutable(current, accountVo);
    validateAccount(accountVo, accountVo.accountId());
    boolean typeChanged =
        !current.getAccountTypeCode().equalsIgnoreCase(accountVo.accountTypeCode());
    String accountClassCode =
        accountTypeMappingService.resolveAccountClassCode(accountVo.accountTypeCode(), typeChanged);
    return accountMapper.update(
        null,
        Wrappers.<Account>lambdaUpdate()
            .eq(Account::getAccountId, accountVo.accountId())
            .set(Account::getAccountName, accountVo.accountName())
            .set(Account::getAccountCode, accountVo.accountCode())
            .set(Account::getIssuer, accountVo.issuer())
            .set(Account::getAccountClassCode, accountClassCode)
            .set(Account::getAccountTypeCode, accountVo.accountTypeCode())
            .set(Account::getCurrency, accountVo.currency())
            .set(Account::getOwnerPersonId, accountVo.ownerPersonId())
            .set(Account::getParentAccountId, accountVo.parentAccountId())
            .set(Account::getAutoDebitAccountId, accountVo.autoDebitAccountId())
            .set(Account::getIsBaseAccount, accountVo.parentAccountId() == null)
            .set(Account::getUpdatedAt, Instant.now()));
  }

  /** Validates all foreign-key and self-reference rules before account persistence. */
  private void validateAccount(AccountVo accountVo, String currentAccountId) {
    if (accountVo == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "账户数据不能为空");
    }
    if (accountVo.ownerPersonId() == null || accountVo.ownerPersonId().isBlank()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "账户归属人不能为空");
    }
    Person owner = personMapper.selectById(accountVo.ownerPersonId());
    if (owner == null || !Boolean.TRUE.equals(owner.getEnabled())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "账户归属人不存在或已停用");
    }
    String parentId = accountVo.parentAccountId();
    String debitId = accountVo.autoDebitAccountId();
    if (currentAccountId != null
        && (currentAccountId.equals(parentId) || currentAccountId.equals(debitId))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "账户不能引用自身");
    }
    if (parentId != null) {
      Account parent = accountMapper.selectById(parentId);
      if (parent == null
          || !Boolean.TRUE.equals(parent.getEnabled())
          || !Boolean.TRUE.equals(parent.getIsBaseAccount())) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "父账户不存在、已停用或不是主账户");
      }
    }
    if (debitId != null) {
      Account debit = accountMapper.selectById(debitId);
      if (debit == null
          || !Boolean.TRUE.equals(debit.getEnabled())
          || !"ASSET".equals(debit.getAccountClassCode())) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "自动扣款账户必须是启用的资产账户");
      }
      if (accountVo.currency() != null
          && debit.getCurrency() != null
          && !debit.getCurrency().equalsIgnoreCase(accountVo.currency())) {
        throw new BusinessException(
            CommonErrorCode.PARAMETER_ERROR, "自动扣款账户币种必须与目标账户币种一致");
      }
    }
  }

  private void assertFinancialSemanticsMutable(Account current, AccountVo requested) {
    boolean currencyChanged = !current.getCurrency().equalsIgnoreCase(requested.currency());
    boolean typeChanged = !current.getAccountTypeCode().equalsIgnoreCase(requested.accountTypeCode());
    boolean parentChanged = !java.util.Objects.equals(current.getParentAccountId(), requested.parentAccountId());

    Long entryCount =
        transactionEntryMapper.selectCount(
            Wrappers.<TransactionEntry>lambdaQuery()
                .eq(TransactionEntry::getAccountId, current.getAccountId()));
    boolean hasFinancialFacts = entryCount != null && entryCount > 0;
    boolean hasFxPosition = fxPositionMapper.selectByAccountId(current.getAccountId()) != null;
    Long childCount =
        accountMapper.selectCount(
            Wrappers.<Account>lambdaQuery()
                .eq(Account::getParentAccountId, current.getAccountId()));
    boolean hasChildren = childCount != null && childCount > 0;

    if ((currencyChanged || typeChanged) && (hasFinancialFacts || hasFxPosition || hasChildren)) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "账户已有财务事实或子账户后不能修改币种/账户类型；请新建账户并迁移业务");
    }

    if (parentChanged) {
      if (hasChildren) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT, "存在子账户时不能把当前主账户改成子账户");
      }
    }
  }

  @Override
  public Integer removeAccount(String accountId) {
    // Loan repayment history is a lifecycle boundary. Keep this semantic guard before
    // the generic entry check so callers get the domain reason instead of a storage detail.
    loanLifecycleDeletionGuard.assertLoanAccountDeletable(accountId);

    Long childCount =
        accountMapper.selectCount(
            Wrappers.<Account>lambdaQuery().eq(Account::getParentAccountId, accountId));

    if (childCount > 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "存在子账户，不能删除");
    }

    Long entryCount =
        transactionEntryMapper.selectCount(
            Wrappers.<TransactionEntry>lambdaQuery().eq(TransactionEntry::getAccountId, accountId));
    if (entryCount != null && entryCount > 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "账户已有财务分录，不能物理删除，请停用账户");
    }

    LoanProfile loanProfile = loanProfileMapper.selectById(accountId);
    if (loanProfile != null) {
      loanRateHistoryMapper.delete(
          Wrappers.<LoanRateHistory>lambdaQuery().eq(LoanRateHistory::getAccountId, accountId));
      loanProfileMapper.deleteById(accountId);
    }

    return accountMapper.deleteById(accountId);
  }

  private void fillOwnerPersons(List<Account> accounts) {
    Map<String, Person> personMap =
        personMapper.selectList(null).stream()
            .collect(Collectors.toMap(Person::getPersonId, Function.identity()));

    accounts.forEach(account -> account.setOwnerPerson(personMap.get(account.getOwnerPersonId())));
  }

  @Override
  public Integer setEnabled(String accountId, boolean enabled) {
    Account account = accountMapper.selectById(accountId);
    if (account == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "账户不存在");
    }

    if (!enabled) {
      Long enabledChildCount =
          accountMapper.selectCount(
              Wrappers.<Account>lambdaQuery()
                  .eq(Account::getParentAccountId, accountId)
                  .eq(Account::getEnabled, true));

      if (enabledChildCount != null && enabledChildCount > 0) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "请先停用该主账户下的子账户");
      }
    }

    return accountMapper.update(
        null,
        Wrappers.<Account>lambdaUpdate()
            .eq(Account::getAccountId, accountId)
            .set(Account::getEnabled, enabled)
            .set(Account::getUpdatedAt, Instant.now()));
  }
}
