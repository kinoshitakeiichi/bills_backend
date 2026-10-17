package com.jiajunxie.service;

import com.jiajunxie.vo.CreditCardProfileVo;
import com.jiajunxie.vo.CreditPaymentRuleVo;
import java.util.List;

public interface CreditCardProfileService {
  CreditCardProfileVo getProfile(String accountId);
  int upsertProfile(String accountId, CreditCardProfileVo vo);
  List<CreditPaymentRuleVo> listRules(String accountId);
  int createRule(String accountId, CreditPaymentRuleVo vo);
  int updateRule(String accountId, String ruleId, CreditPaymentRuleVo vo);
  int setRuleEnabled(String accountId, String ruleId, boolean enabled);
}
