package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.convert.LoanProfileConvertServiceMapper;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.DictionaryType;
import com.jiajunxie.entity.LoanProfile;
import com.jiajunxie.entity.LoanRateHistory;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.BusinessCalendarMapper;
import com.jiajunxie.mapper.DictionaryItemMapper;
import com.jiajunxie.mapper.DictionaryTypeMapper;
import com.jiajunxie.mapper.LoanProfileMapper;
import com.jiajunxie.mapper.LoanRateHistoryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.vo.LoanProfileVo;
import com.jiajunxie.vo.LoanRateHistoryVo;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LoanProfileServiceImplTest {
  private AccountMapper accountMapper;
  private LoanProfileMapper profileMapper;
  private LoanRateHistoryMapper rateHistoryMapper;
  private DictionaryTypeMapper dictionaryTypeMapper;
  private DictionaryItemMapper dictionaryItemMapper;
  private LoanProfileConvertServiceMapper convertMapper;
  private TransactionMapper transactionMapper;
  private LoanProfileServiceImpl service;

  @BeforeEach
  void setUp() {
    accountMapper = mock(AccountMapper.class);
    profileMapper = mock(LoanProfileMapper.class);
    rateHistoryMapper = mock(LoanRateHistoryMapper.class);
    dictionaryTypeMapper = mock(DictionaryTypeMapper.class);
    dictionaryItemMapper = mock(DictionaryItemMapper.class);
    convertMapper = mock(LoanProfileConvertServiceMapper.class);
    transactionMapper = mock(TransactionMapper.class);
    service =
        new LoanProfileServiceImpl(
            accountMapper,
            profileMapper,
            rateHistoryMapper,
            mock(BusinessCalendarMapper.class),
            dictionaryTypeMapper,
            dictionaryItemMapper,
            convertMapper,
            transactionMapper);
  }

  @Test
  void assetAccountCannotOwnLoanProfile() {
    when(accountMapper.selectById("bank")).thenReturn(account("bank", "ASSET", "BANK", "JPY"));

    assertThrows(BusinessException.class, () -> service.upsertProfile("bank", validProfile("FIXED")));
    verify(profileMapper, never()).insert(any(LoanProfile.class));
  }



  @Test
  void fundingModeCanChangeBeforeFactsButNotAfterFunding() {
    stubValidMasterData();
    when(accountMapper.selectById("loan")).thenReturn(account("loan", "LIABILITY", "CONSUMER_LOAN", "JPY"));
    LoanProfile existing = entityFrom(validProfile("FIXED"));
    existing.setAccountId("loan");
    when(profileMapper.selectById("loan")).thenReturn(existing);
    when(transactionMapper.selectCount(any())).thenReturn(0L, 1L);
    when(profileMapper.updateById(any(LoanProfile.class))).thenReturn(1);
    LoanProfileVo direct = validProfile("FIXED");
    direct.setFundingModeCode("DIRECT_FINANCING");

    assertEquals(1, service.upsertProfile("loan", direct));

    existing.setFundingModeCode("ACCOUNT_DISBURSEMENT");
    assertThrows(BusinessException.class, () -> service.upsertProfile("loan", direct));
  }



  @Test
  void originalPrincipalCanChangeBeforeFundingButIsLockedAfterFunding() {
    stubValidMasterData();
    when(accountMapper.selectById("loan")).thenReturn(account("loan", "LIABILITY", "LOAN", "JPY"));
    LoanProfile existing = entityFrom(validProfile("FIXED"));
    existing.setAccountId("loan");
    when(profileMapper.selectById("loan")).thenReturn(existing);
    when(profileMapper.updateById(any(LoanProfile.class))).thenReturn(1);
    when(transactionMapper.selectCount(any())).thenReturn(0L, 1L);

    LoanProfileVo changed = validProfile("FIXED");
    changed.setOriginalPrincipal(new BigDecimal("32000000.0000"));

    assertEquals(1, service.upsertProfile("loan", changed));

    existing.setOriginalPrincipal(new BigDecimal("30000000.0000"));
    assertThrows(BusinessException.class, () -> service.upsertProfile("loan", changed));
  }

  @Test
  void purchaseFinancingAllowsNullOriginalPrincipal() {
    stubValidMasterData();
    when(accountMapper.selectById("loan")).thenReturn(account("loan", "LIABILITY", "CONSUMER_LOAN", "JPY"));
    when(profileMapper.selectById("loan")).thenReturn(null);
    LoanProfileVo vo = validProfile("FIXED");
    vo.setFundingModeCode("PURCHASE_FINANCING");
    vo.setOriginalPrincipal(null);
    LoanProfile entity = entityFrom(vo);
    when(convertMapper.toEntity(any())).thenReturn(entity);
    when(profileMapper.insert(any(LoanProfile.class))).thenReturn(1);

    assertEquals(1, service.upsertProfile("loan", vo));
    verify(profileMapper).insert(any(LoanProfile.class));
  }

  @Test
  void variableProfileCreatesInitialRateHistory() {
    stubValidMasterData();
    when(accountMapper.selectById("loan")).thenReturn(account("loan", "LIABILITY", "MORTGAGE", "JPY"));
    when(profileMapper.selectById("loan")).thenReturn(null);
    LoanProfile entity = entityFrom(validProfile("VARIABLE"));
    when(convertMapper.toEntity(any())).thenReturn(entity);
    when(profileMapper.insert(any(LoanProfile.class))).thenReturn(1);
    when(rateHistoryMapper.insert(any(LoanRateHistory.class))).thenReturn(1);

    assertEquals(1, service.upsertProfile("loan", validProfile("VARIABLE")));

    ArgumentCaptor<LoanRateHistory> captor = ArgumentCaptor.forClass(LoanRateHistory.class);
    verify(rateHistoryMapper).insert(captor.capture());
    assertEquals("loan", captor.getValue().getAccountId());
    assertEquals(LocalDate.of(2026, 1, 1), captor.getValue().getEffectiveDate());
    assertEquals(new BigDecimal("0.01250000"), captor.getValue().getAnnualRate());
  }

  @Test
  void variableRateCannotBeOverwrittenThroughProfile() {
    stubValidMasterData();
    when(accountMapper.selectById("loan")).thenReturn(account("loan", "LIABILITY", "LOAN", "JPY"));
    LoanProfile existing = entityFrom(validProfile("VARIABLE"));
    existing.setAccountId("loan");
    when(profileMapper.selectById("loan")).thenReturn(existing);
    LoanProfileVo changed = validProfile("VARIABLE");
    changed.setAnnualRate(new BigDecimal("0.02000000"));

    assertThrows(BusinessException.class, () -> service.upsertProfile("loan", changed));
    verify(profileMapper, never()).updateById(any(LoanProfile.class));
  }

  @Test
  void fixedLoanRejectsRateHistory() {
    when(accountMapper.selectById("loan")).thenReturn(account("loan", "LIABILITY", "LOAN", "JPY"));
    LoanProfile profile = entityFrom(validProfile("FIXED"));
    profile.setAccountId("loan");
    when(profileMapper.selectById("loan")).thenReturn(profile);

    assertThrows(BusinessException.class, () -> service.addRateHistory("loan", rate(LocalDate.now(), "0.02")));
    verify(rateHistoryMapper, never()).insert(any(LoanRateHistory.class));
  }

  @Test
  void futureVariableRateDoesNotChangeCurrentProfileRate() {
    when(accountMapper.selectById("loan")).thenReturn(account("loan", "LIABILITY", "LOAN", "JPY"));
    LoanProfile profile = entityFrom(validProfile("VARIABLE"));
    profile.setAccountId("loan");
    when(profileMapper.selectById("loan")).thenReturn(profile);
    when(rateHistoryMapper.selectCount(any())).thenReturn(0L);
    when(rateHistoryMapper.insert(any(LoanRateHistory.class))).thenReturn(1);
    stubRateEntityConversion();

    assertEquals(1, service.addRateHistory("loan", rate(LocalDate.now().plusDays(10), "0.02")));

    verify(profileMapper, never()).updateById(any(LoanProfile.class));
    assertEquals(new BigDecimal("0.01250000"), profile.getAnnualRate());
  }

  @Test
  void effectiveVariableRateRefreshesProfileFromLatestApplicableHistory() {
    when(accountMapper.selectById("loan")).thenReturn(account("loan", "LIABILITY", "LOAN", "JPY"));
    LoanProfile profile = entityFrom(validProfile("VARIABLE"));
    profile.setAccountId("loan");
    when(profileMapper.selectById("loan")).thenReturn(profile);
    when(rateHistoryMapper.selectCount(any())).thenReturn(0L);
    when(rateHistoryMapper.insert(any(LoanRateHistory.class))).thenReturn(1);
    stubRateEntityConversion();
    LoanRateHistory latest = new LoanRateHistory();
    latest.setAccountId("loan");
    latest.setEffectiveDate(LocalDate.now());
    latest.setAnnualRate(new BigDecimal("0.02000000"));
    when(rateHistoryMapper.selectOne(any())).thenReturn(latest);

    assertEquals(1, service.addRateHistory("loan", rate(LocalDate.now(), "0.02")));

    verify(profileMapper).updateById(profile);
    assertEquals(new BigDecimal("0.02000000"), profile.getAnnualRate());
  }

  @Test
  void duplicateEffectiveDateIsRejected() {
    when(accountMapper.selectById("loan")).thenReturn(account("loan", "LIABILITY", "LOAN", "JPY"));
    LoanProfile profile = entityFrom(validProfile("VARIABLE"));
    profile.setAccountId("loan");
    when(profileMapper.selectById("loan")).thenReturn(profile);
    when(rateHistoryMapper.selectCount(any())).thenReturn(1L);

    assertThrows(BusinessException.class, () -> service.addRateHistory("loan", rate(LocalDate.now(), "0.02")));
    verify(rateHistoryMapper, never()).insert(any(LoanRateHistory.class));
  }

  private void stubValidMasterData() {
    DictionaryType type = new DictionaryType();
    type.setDictionaryTypeId("type");
    when(dictionaryTypeMapper.selectOne(any())).thenReturn(type);
    when(dictionaryItemMapper.selectCount(any())).thenReturn(1L);
  }

  private Account account(String id, String accountClass, String accountType, String currency) {
    Account account = new Account();
    account.setAccountId(id);
    account.setAccountClassCode(accountClass);
    account.setAccountTypeCode(accountType);
    account.setCurrency(currency);
    account.setEnabled(true);
    return account;
  }

  private LoanProfileVo validProfile(String rateType) {
    LoanProfileVo vo = new LoanProfileVo();
    vo.setLoanTypeCode("MORTGAGE");
    vo.setFundingModeCode("ACCOUNT_DISBURSEMENT");
    vo.setOriginalPrincipal(new BigDecimal("30000000.0000"));
    vo.setCurrency("JPY");
    vo.setRateTypeCode(rateType);
    vo.setAnnualRate(new BigDecimal("0.01250000"));
    vo.setStartDate(LocalDate.of(2026, 1, 1));
    vo.setMaturityDate(LocalDate.of(2056, 1, 1));
    vo.setPaymentFrequencyCode("MONTHLY");
    vo.setPaymentDay(27);
    return vo;
  }

  private LoanProfile entityFrom(LoanProfileVo vo) {
    LoanProfile entity = new LoanProfile();
    entity.setLoanTypeCode(vo.getLoanTypeCode());
    entity.setFundingModeCode(vo.getFundingModeCode());
    entity.setOriginalPrincipal(vo.getOriginalPrincipal());
    entity.setCurrency(vo.getCurrency());
    entity.setRateTypeCode(vo.getRateTypeCode());
    entity.setAnnualRate(vo.getAnnualRate());
    entity.setStartDate(vo.getStartDate());
    entity.setMaturityDate(vo.getMaturityDate());
    entity.setPaymentFrequencyCode(vo.getPaymentFrequencyCode());
    entity.setPaymentDay(vo.getPaymentDay());
    return entity;
  }

  private void stubRateEntityConversion() {
    when(convertMapper.toRateEntity(any(LoanRateHistoryVo.class)))
        .thenAnswer(
            invocation -> {
              LoanRateHistoryVo source = invocation.getArgument(0);
              LoanRateHistory entity = new LoanRateHistory();
              entity.setEffectiveDate(source.getEffectiveDate());
              entity.setAnnualRate(source.getAnnualRate());
              return entity;
            });
  }

  private LoanRateHistoryVo rate(LocalDate effectiveDate, String annualRate) {
    LoanRateHistoryVo vo = new LoanRateHistoryVo();
    vo.setEffectiveDate(effectiveDate);
    vo.setAnnualRate(new BigDecimal(annualRate));
    return vo;
  }
}
