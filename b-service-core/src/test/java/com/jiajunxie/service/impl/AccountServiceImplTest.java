package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.convert.AccountConvertServiceMapper;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.LoanProfile;
import com.jiajunxie.entity.Person;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.FxPositionMapper;
import com.jiajunxie.mapper.LoanProfileMapper;
import com.jiajunxie.mapper.LoanRateHistoryMapper;
import com.jiajunxie.mapper.PersonMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.service.AccountTypeMappingService;
import com.jiajunxie.vo.AccountInfoVo;
import com.jiajunxie.vo.AccountTypeMappingVo;
import com.jiajunxie.vo.AccountVo;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

  @Mock AccountMapper accountMapper;
  @Mock AccountConvertServiceMapper accountConvertMapper;
  @Mock PersonMapper personMapper;
  @Mock AccountTypeMappingService accountTypeMappingService;
  @Mock TransactionEntryMapper transactionEntryMapper;
  @Mock LoanProfileMapper loanProfileMapper;
  @Mock LoanRateHistoryMapper loanRateHistoryMapper;
  @Mock LoanLifecycleDeletionGuard loanLifecycleDeletionGuard;
  @Mock FxPositionMapper fxPositionMapper;

  private AccountServiceImpl service() {
    return new AccountServiceImpl(
        accountMapper,
        accountConvertMapper,
        personMapper,
        accountTypeMappingService,
        transactionEntryMapper,
        loanProfileMapper,
        loanRateHistoryMapper,
        loanLifecycleDeletionGuard,
        fxPositionMapper);
  }

  @Test
  void cardListEnrichesInternalCodesAndIdsWithMasterDataDisplayNames() {
    Account account = new Account();
    account.setAccountId("a1");
    account.setAccountName("EPOS");
    account.setAccountCode("EPOS-JPY");
    account.setAccountClassCode("LIABILITY");
    account.setAccountTypeCode("CREDIT_CARD");
    account.setCurrency("JPY");
    account.setTotalAmount(BigDecimal.ZERO);
    account.setOwnerPersonId("p1");

    Person owner = new Person();
    owner.setPersonId("p1");
    owner.setPersonName("本人");
    owner.setEnabled(false); // Historical display must also work for disabled master data.

    AccountTypeMappingVo mapping =
        new AccountTypeMappingVo(
            "type-item", "CREDIT_CARD", "信用卡", "class-item", "LIABILITY", "负债");

    AccountInfoVo expected =
        new AccountInfoVo(
            "a1",
            "EPOS",
            "EPOS-JPY",
            null,
            "LIABILITY",
            "负债",
            "CREDIT_CARD",
            "信用卡",
            BigDecimal.ZERO,
            "JPY",
            "p1",
            "本人",
            null,
            null,
            true);

    when(accountMapper.selectList(null)).thenReturn(List.of(account));
    when(personMapper.selectByIds(List.of("p1"))).thenReturn(List.of(owner));
    when(accountTypeMappingService.listMappings()).thenReturn(List.of(mapping));
    when(accountConvertMapper.toInfoVo(account, "本人", "负债", "信用卡")).thenReturn(expected);

    assertEquals(List.of(expected), service().cardList());
    verify(accountConvertMapper).toInfoVo(account, "本人", "负债", "信用卡");
  }

  @Test
  void loanProfileWithoutFinancialEntriesIsDeletedWithAccount() {
    LoanProfile profile = new LoanProfile();
    profile.setAccountId("loan");
    when(accountMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
    when(transactionEntryMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
    when(loanProfileMapper.selectById("loan")).thenReturn(profile);
    when(loanRateHistoryMapper.delete(org.mockito.ArgumentMatchers.any())).thenReturn(1);
    when(loanProfileMapper.deleteById("loan")).thenReturn(1);
    when(accountMapper.deleteById("loan")).thenReturn(1);

    assertEquals(1, service().removeAccount("loan"));

    verify(loanRateHistoryMapper).delete(org.mockito.ArgumentMatchers.any());
    verify(loanProfileMapper).deleteById("loan");
    verify(accountMapper).deleteById("loan");
  }

  @Test
  void accountWithFinancialEntriesCannotBePhysicallyDeleted() {
    when(accountMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
    when(transactionEntryMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(1L);

    assertThrows(BusinessException.class, () -> service().removeAccount("loan"));

    verify(loanProfileMapper, never()).deleteById("loan");
    verify(accountMapper, never()).deleteById("loan");
  }
@Test
void loanWithPaymentHistoryIsRejectedBeforeGenericEntryDeletionChecks() {
  org.mockito.Mockito.doThrow(
          new BusinessException(
              com.jiajunxie.common.exception.CommonErrorCode.DATA_CONFLICT,
              "贷款已经产生实际还款明细，不能删除贷款"))
      .when(loanLifecycleDeletionGuard)
      .assertLoanAccountDeletable("loan");

  assertThrows(BusinessException.class, () -> service().removeAccount("loan"));

  verify(transactionEntryMapper, never()).selectCount(org.mockito.ArgumentMatchers.any());
  verify(loanProfileMapper, never()).deleteById("loan");
  verify(accountMapper, never()).deleteById("loan");
}

  @Test
  void accountWithFinancialFactsCannotChangeTypeOrCurrency() {
    Account current = new Account();
    current.setAccountId("a1");
    current.setAccountTypeCode("CREDIT_CARD");
    current.setAccountClassCode("LIABILITY");
    current.setCurrency("JPY");
    current.setParentAccountId(null);
    when(accountMapper.selectById("a1")).thenReturn(current);
    when(transactionEntryMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(1L);

    AccountVo requested =
        new AccountVo(
            "a1", "Card", "CARD", null, null, "BANK", "JPY", "p1", null, null);

    assertThrows(BusinessException.class, () -> service().updateMasterData(requested));
    verify(accountMapper, never()).update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void baseAccountWithChildrenCannotBecomeChildAccount() {
    Account current = new Account();
    current.setAccountId("base");
    current.setAccountTypeCode("BANK");
    current.setAccountClassCode("ASSET");
    current.setCurrency("JPY");
    current.setParentAccountId(null);
    when(accountMapper.selectById("base")).thenReturn(current);
    when(accountMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(1L);

    AccountVo requested =
        new AccountVo(
            "base", "Base", "BASE", null, null, "BANK", "JPY", "p1", "other-base", null);

    assertThrows(BusinessException.class, () -> service().updateMasterData(requested));
  }

}
