package com.jiajunxie.service;

import com.jiajunxie.vo.ExchangeRateAlertRuleVo;
import com.jiajunxie.vo.ExchangeRateAlertSettingsVo;
import java.util.List;

public interface ExchangeRateAlertService {

  ExchangeRateAlertSettingsVo getSettings();

  ExchangeRateAlertSettingsVo updateSettings(ExchangeRateAlertSettingsVo settings);

  List<ExchangeRateAlertRuleVo> listRules(Boolean enabled);

  ExchangeRateAlertRuleVo createRule(ExchangeRateAlertRuleVo rule);

  ExchangeRateAlertRuleVo updateRule(String alertRuleId, ExchangeRateAlertRuleVo rule);

  int setRuleEnabled(String alertRuleId, boolean enabled);

  int deleteRule(String alertRuleId);

  void evaluateEnabledRules();
}
