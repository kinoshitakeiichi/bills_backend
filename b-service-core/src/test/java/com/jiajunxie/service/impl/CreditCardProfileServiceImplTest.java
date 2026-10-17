
package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.convert.CreditCardProfileConvertServiceMapper;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.DictionaryItem;
import com.jiajunxie.entity.DictionaryType;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.BusinessCalendarMapper;
import com.jiajunxie.mapper.CreditCardProfileMapper;
import com.jiajunxie.mapper.CreditPaymentRuleMapper;
import com.jiajunxie.mapper.DictionaryItemMapper;
import com.jiajunxie.mapper.DictionaryTypeMapper;
import com.jiajunxie.vo.CreditCardProfileVo;
import org.junit.jupiter.api.Test;

class CreditCardProfileServiceImplTest {
  @Test
  void bankAccountCannotOwnCreditCardProfile() {
    AccountMapper accountMapper = mock(AccountMapper.class);
    Account bank = new Account();
    bank.setAccountId("bank");
    bank.setAccountTypeCode("BANK");
    bank.setAccountClassCode("ASSET");
    bank.setEnabled(true);
    when(accountMapper.selectById("bank")).thenReturn(bank);

    var service = service(accountMapper);
    assertThrows(BusinessException.class, () -> service.upsertProfile("bank", validProfile()));
  }

  private CreditCardProfileServiceImpl service(AccountMapper accountMapper) {
    return new CreditCardProfileServiceImpl(
        accountMapper,
        mock(CreditCardProfileMapper.class),
        mock(CreditPaymentRuleMapper.class),
        mock(BusinessCalendarMapper.class),
        mock(DictionaryTypeMapper.class),
        mock(DictionaryItemMapper.class),
        mock(CreditCardProfileConvertServiceMapper.class));
  }

  private CreditCardProfileVo validProfile() {
    CreditCardProfileVo vo = new CreditCardProfileVo();
    vo.setAmountScale(0);
    vo.setBillingStartDay(1);
    vo.setBillingEndDay(31);
    vo.setBillingEndMonthOffset(0);
    vo.setConfirmationDay(5);
    vo.setConfirmationMonthOffset(1);
    vo.setPaymentDay(10);
    vo.setPaymentMonthOffset(1);
    vo.setInvalidDayRuleCode("LAST_DAY");
    vo.setBusinessDayAdjustmentCode("NONE");
    vo.setTimezone("Asia/Tokyo");
    vo.setRemainderRuleCode("LAST_INSTALLMENT");
    vo.setEnabled(true);
    return vo;
  }
}
