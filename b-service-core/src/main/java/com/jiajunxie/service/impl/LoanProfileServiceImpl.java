package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.convert.LoanProfileConvertServiceMapper;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.BusinessCalendar;
import com.jiajunxie.entity.DictionaryItem;
import com.jiajunxie.entity.DictionaryType;
import com.jiajunxie.entity.LoanProfile;
import com.jiajunxie.entity.LoanRateHistory;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.BusinessCalendarMapper;
import com.jiajunxie.mapper.DictionaryItemMapper;
import com.jiajunxie.mapper.DictionaryTypeMapper;
import com.jiajunxie.mapper.LoanProfileMapper;
import com.jiajunxie.mapper.LoanRateHistoryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.LoanProfileService;
import com.jiajunxie.vo.LoanProfileVo;
import com.jiajunxie.vo.LoanRateHistoryVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class LoanProfileServiceImpl implements LoanProfileService {
  private static final Set<String> LOAN_ACCOUNT_TYPES = Set.of("LOAN", "MORTGAGE", "CONSUMER_LOAN");

  private final AccountMapper accountMapper;
  private final LoanProfileMapper profileMapper;
  private final LoanRateHistoryMapper rateHistoryMapper;
  private final BusinessCalendarMapper calendarMapper;
  private final DictionaryTypeMapper dictionaryTypeMapper;
  private final DictionaryItemMapper dictionaryItemMapper;
  private final LoanProfileConvertServiceMapper convertMapper;
  private final TransactionMapper transactionMapper;

  @Override
  public LoanProfileVo getProfile(String accountId) {
    LoanProfile profile = profileMapper.selectById(accountId);
    return profile == null ? null : convertMapper.toVo(profile);
  }

  @Override
  @Transactional
  public int upsertProfile(String accountId, LoanProfileVo vo) {
    Account account = requireLoanAccount(accountId);
    validateProfile(account, vo);
    LoanProfile existing = profileMapper.selectById(accountId);
    Instant now = Instant.now();

    if (existing == null) {
      LoanProfile entity = convertMapper.toEntity(vo);
      entity.setAccountId(accountId);
      entity.setCurrency(account.getCurrency());
      entity.setCreatedAt(now);
      entity.setUpdatedAt(now);
      int result = profileMapper.insert(entity);
      if ("VARIABLE".equals(entity.getRateTypeCode())) {
        insertInitialVariableRate(entity);
      }
      return result;
    }

    boolean fundingFacts = hasFundingFacts(accountId);
    if (!existing.getFundingModeCode().equals(vo.getFundingModeCode()) && fundingFacts) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "已有放款/融资消费事实后不能修改资金形成方式");
    }
    if (fundingFacts
        && !sameAmount(existing.getOriginalPrincipal(), vo.getOriginalPrincipal())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "已有放款/融资消费事实后不能修改初始本金");
    }
    if (!existing.getRateTypeCode().equals(vo.getRateTypeCode())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款利率类型创建后不能直接修改");
    }
    if ("VARIABLE".equals(existing.getRateTypeCode())
        && existing.getAnnualRate().compareTo(vo.getAnnualRate()) != 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "浮动利率请通过利率历史接口维护");
    }

    Instant createdAt = existing.getCreatedAt();
    BigDecimal currentVariableRate = existing.getAnnualRate();
    convertMapper.updateEntity(vo, existing);
    existing.setAccountId(accountId);
    existing.setCurrency(account.getCurrency());
    existing.setCreatedAt(createdAt);
    if ("VARIABLE".equals(existing.getRateTypeCode())) {
      existing.setAnnualRate(currentVariableRate);
    }
    existing.setUpdatedAt(now);
    return profileMapper.updateById(existing);
  }

  @Override
  public List<LoanRateHistoryVo> listRateHistory(String accountId) {
    requireExistingProfile(accountId);
    return convertMapper.toRateVos(
        rateHistoryMapper.selectList(
            Wrappers.<LoanRateHistory>lambdaQuery()
                .eq(LoanRateHistory::getAccountId, accountId)
                .orderByDesc(LoanRateHistory::getEffectiveDate)
                .orderByDesc(LoanRateHistory::getCreatedAt)));
  }

  @Override
  @Transactional
  public int addRateHistory(String accountId, LoanRateHistoryVo vo) {
    requireLoanAccount(accountId);
    LoanProfile profile = requireExistingProfile(accountId);
    if (!"VARIABLE".equals(profile.getRateTypeCode())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "只有浮动利率贷款可以维护利率历史");
    }
    if (vo == null || vo.getEffectiveDate() == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "利率生效日不能为空");
    }
    if (vo.getEffectiveDate().isBefore(profile.getStartDate())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "利率生效日不能早于贷款开始日");
    }
    if (vo.getAnnualRate() == null || vo.getAnnualRate().signum() < 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "年利率不能为负数");
    }
    long duplicate = rateHistoryMapper.selectCount(
        Wrappers.<LoanRateHistory>lambdaQuery()
            .eq(LoanRateHistory::getAccountId, accountId)
            .eq(LoanRateHistory::getEffectiveDate, vo.getEffectiveDate()));
    if (duplicate > 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "同一生效日只能存在一条贷款利率记录");
    }

    LoanRateHistory entity = convertMapper.toRateEntity(vo);
    entity.setRateHistoryId(UUID.randomUUID().toString());
    entity.setAccountId(accountId);
    entity.setCreatedAt(Instant.now());
    int result = rateHistoryMapper.insert(entity);

    if (!entity.getEffectiveDate().isAfter(LocalDate.now())) {
      LoanRateHistory current = latestEffectiveRate(accountId, LocalDate.now());
      if (current != null) {
        profile.setAnnualRate(current.getAnnualRate());
        profile.setUpdatedAt(Instant.now());
        profileMapper.updateById(profile);
      }
    }
    return result;
  }

  private void validateProfile(Account account, LoanProfileVo vo) {
    if (vo == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "贷款配置不能为空");
    }
    vo.setLoanTypeCode(normalizeUpper(vo.getLoanTypeCode(), "贷款产品类型不能为空"));
    vo.setFundingModeCode(normalizeUpper(vo.getFundingModeCode(), "贷款资金形成方式不能为空"));
    vo.setRateTypeCode(normalizeUpper(vo.getRateTypeCode(), "利率类型不能为空"));
    vo.setPaymentFrequencyCode(normalizeUpper(vo.getPaymentFrequencyCode(), "还款频率不能为空"));
    vo.setBusinessDayAdjustmentCode(normalizeOptionalUpper(vo.getBusinessDayAdjustmentCode()));
    vo.setCalendarCode(normalizeOptionalUpper(vo.getCalendarCode()));

    requireEnabledDictionaryCode("LOAN_TYPE", vo.getLoanTypeCode());
    requireEnabledDictionaryCode("LOAN_FUNDING_MODE", vo.getFundingModeCode());
    requireEnabledDictionaryCode("LOAN_RATE_TYPE", vo.getRateTypeCode());
    requireEnabledDictionaryCode("LOAN_PAYMENT_FREQUENCY", vo.getPaymentFrequencyCode());
    if (vo.getBusinessDayAdjustmentCode() != null) {
      requireEnabledDictionaryCode("BUSINESS_DAY_ADJUSTMENT", vo.getBusinessDayAdjustmentCode());
    }

    if ("PURCHASE_FINANCING".equals(vo.getFundingModeCode())) {
      if (vo.getOriginalPrincipal() != null) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "逐笔消费融资不设置固定初始本金");
      }
    } else if (vo.getOriginalPrincipal() == null || vo.getOriginalPrincipal().signum() <= 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "该资金形成方式必须设置大于 0 的初始本金");
    }
    if (vo.getAnnualRate() == null || vo.getAnnualRate().signum() < 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "年利率不能为负数");
    }
    if (vo.getStartDate() == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "贷款开始日不能为空");
    }
    if (vo.getMaturityDate() != null && vo.getMaturityDate().isBefore(vo.getStartDate())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "贷款到期日不能早于开始日");
    }
    if ("MONTHLY".equals(vo.getPaymentFrequencyCode())) {
      if (vo.getPaymentDay() == null || vo.getPaymentDay() < 1 || vo.getPaymentDay() > 31) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "按月还款的还款日必须为 1-31");
      }
    }
    if (vo.getCalendarCode() != null) {
      BusinessCalendar calendar = calendarMapper.selectOne(
          Wrappers.<BusinessCalendar>lambdaQuery()
              .eq(BusinessCalendar::getCalendarCode, vo.getCalendarCode()));
      if (calendar == null) {
        throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "营业日日历不存在：" + vo.getCalendarCode());
      }
      if (!Boolean.TRUE.equals(calendar.getEnabled())) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "营业日日历已停用：" + vo.getCalendarCode());
      }
    }
    if (vo.getCalendarCode() != null && vo.getBusinessDayAdjustmentCode() == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "配置营业日日历时必须指定营业日调整规则");
    }
    if (vo.getCurrency() != null && !account.getCurrency().equalsIgnoreCase(vo.getCurrency().trim())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "贷款配置币种必须与贷款账户币种一致");
    }
    vo.setCurrency(account.getCurrency());
  }


  private boolean sameAmount(BigDecimal left, BigDecimal right) {
    if (left == null || right == null) return left == null && right == null;
    return left.compareTo(right) == 0;
  }

  private boolean hasFundingFacts(String accountId) {
    Long count = transactionMapper.selectCount(
        Wrappers.<Transaction>lambdaQuery()
            .eq(Transaction::getOriginReferenceId, accountId)
            .in(Transaction::getTransactionType, "LOAN_DISBURSEMENT", "LOAN_PURCHASE"));
    return count != null && count > 0;
  }

  private Account requireLoanAccount(String accountId) {
    Account account = accountMapper.selectById(accountId);
    if (account == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "贷款账户不存在");
    }
    if (!Boolean.TRUE.equals(account.getEnabled())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款账户已停用");
    }
    if (!"LIABILITY".equalsIgnoreCase(account.getAccountClassCode())
        || !LOAN_ACCOUNT_TYPES.contains(account.getAccountTypeCode().toUpperCase(Locale.ROOT))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "只有启用的贷款类负债账户可以维护贷款配置");
    }
    return account;
  }

  private LoanProfile requireExistingProfile(String accountId) {
    LoanProfile profile = profileMapper.selectById(accountId);
    if (profile == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "贷款配置不存在");
    }
    return profile;
  }

  private void insertInitialVariableRate(LoanProfile profile) {
    LoanRateHistory initial = new LoanRateHistory();
    initial.setRateHistoryId(UUID.randomUUID().toString());
    initial.setAccountId(profile.getAccountId());
    initial.setEffectiveDate(profile.getStartDate());
    initial.setAnnualRate(profile.getAnnualRate());
    initial.setCreatedAt(Instant.now());
    rateHistoryMapper.insert(initial);
  }

  private LoanRateHistory latestEffectiveRate(String accountId, LocalDate date) {
    return rateHistoryMapper.selectOne(
        Wrappers.<LoanRateHistory>lambdaQuery()
            .eq(LoanRateHistory::getAccountId, accountId)
            .le(LoanRateHistory::getEffectiveDate, date)
            .orderByDesc(LoanRateHistory::getEffectiveDate)
            .last("LIMIT 1"));
  }

  private void requireEnabledDictionaryCode(String typeCode, String itemCode) {
    DictionaryType type = dictionaryTypeMapper.selectOne(
        Wrappers.<DictionaryType>lambdaQuery()
            .eq(DictionaryType::getTypeCode, typeCode)
            .eq(DictionaryType::getEnabled, true));
    if (type == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "字典类型不存在或已停用：" + typeCode);
    }
    long count = dictionaryItemMapper.selectCount(
        Wrappers.<DictionaryItem>lambdaQuery()
            .eq(DictionaryItem::getDictionaryTypeId, type.getDictionaryTypeId())
            .eq(DictionaryItem::getItemCode, itemCode)
            .eq(DictionaryItem::getEnabled, true));
    if (count == 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "字典项不存在或已停用：" + typeCode + "/" + itemCode);
    }
  }

  private String normalizeUpper(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, message);
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeOptionalUpper(String value) {
    return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
  }
}
