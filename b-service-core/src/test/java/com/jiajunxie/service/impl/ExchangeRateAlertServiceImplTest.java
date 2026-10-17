package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.jiajunxie.entity.ExchangeRateAlertPairState;
import com.jiajunxie.entity.ExchangeRateAlertRule;
import com.jiajunxie.entity.ExchangeRateAlertSettings;
import com.jiajunxie.mapper.ExchangeRateAlertPairStateMapper;
import com.jiajunxie.mapper.ExchangeRateAlertRuleMapper;
import com.jiajunxie.mapper.ExchangeRateAlertSettingsMapper;
import com.jiajunxie.mapper.CurrencyInventoryBucketMapper;
import com.jiajunxie.mapper.CurrencyInventoryRebuildMapper;
import com.jiajunxie.mapper.FxPositionMapper;
import com.jiajunxie.service.BarkNotificationService;
import com.jiajunxie.service.DictionaryService;
import com.jiajunxie.service.ExchangeRateService;
import com.jiajunxie.vo.BarkNotificationVo;
import com.jiajunxie.vo.DictionaryOptionVo;
import com.jiajunxie.vo.ExchangeRateAlertSettingsVo;
import com.jiajunxie.vo.RealtimeExchangeRateVo;

@ExtendWith(MockitoExtension.class)
class ExchangeRateAlertServiceImplTest {

  @Mock ExchangeRateAlertSettingsMapper settingsMapper;
  @Mock ExchangeRateAlertRuleMapper ruleMapper;
  @Mock ExchangeRateAlertPairStateMapper pairStateMapper;
  @Mock FxPositionMapper fxPositionMapper;
  @Mock CurrencyInventoryBucketMapper currencyInventoryBucketMapper;
  @Mock CurrencyInventoryRebuildMapper currencyInventoryRebuildMapper;
  @Mock DictionaryService dictionaryService;
  @Mock ExchangeRateService exchangeRateService;
  @Mock BarkNotificationService barkNotificationService;
  @Mock ApplicationEventPublisher eventPublisher;

  private ExchangeRateAlertServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new ExchangeRateAlertServiceImpl(
            settingsMapper,
            ruleMapper,
            pairStateMapper,
            fxPositionMapper,
            currencyInventoryBucketMapper,
            currencyInventoryRebuildMapper,
            dictionaryService,
            exchangeRateService,
            barkNotificationService,
            new AlertTemplateRenderer(),
            eventPublisher);

    org.mockito.Mockito.lenient()
        .when(dictionaryService.listOptions(anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              String code = invocation.getArgument(1);
              return List.of(new DictionaryOptionVo(code, code));
            });
    org.mockito.Mockito.lenient()
        .when(pairStateMapper.updateById(any(ExchangeRateAlertPairState.class)))
        .thenReturn(1);
    org.mockito.Mockito.lenient()
        .when(ruleMapper.updateById(any(ExchangeRateAlertRule.class)))
        .thenReturn(1);
    org.mockito.Mockito.lenient()
        .when(currencyInventoryRebuildMapper.selectInventoryCutoverAppliedAt())
        .thenReturn(null);
    org.mockito.Mockito.lenient()
        .when(fxPositionMapper.selectSystemWeightedAverageRate(anyString(), anyString()))
        .thenReturn(null);
    org.mockito.Mockito.lenient()
        .when(currencyInventoryBucketMapper.selectSystemWeightedAverageRate(anyString(), anyString()))
        .thenReturn(null);
  }

  @Test
  void updateSettingsPublishesDynamicRescheduleEvent() {
    when(settingsMapper.selectById("DEFAULT")).thenReturn(settings());
    when(settingsMapper.updateById(any(ExchangeRateAlertSettings.class))).thenReturn(1);
    when(barkNotificationService.isConfigured()).thenReturn(true);

    var updated =
        service.updateSettings(
            new ExchangeRateAlertSettingsVo(
                true,
                120,
                30,
                "TRADINGVIEW",
                "BARK",
                true,
                "FX",
                "{currentRate}",
                null,
                "https://api.day.app/push",
                null,
                null,
                "FX",
                "active",
                1,
                2.0,
                false));

    assertEquals(120, updated.pollingIntervalSeconds());
    assertTrue(updated.barkDeviceKeyConfigured());

    ArgumentCaptor<ExchangeRateAlertSettingsChangedEvent> event =
        ArgumentCaptor.forClass(ExchangeRateAlertSettingsChangedEvent.class);
    verify(eventPublisher).publishEvent(event.capture());
    assertEquals(120, event.getValue().pollingIntervalSeconds());
  }

  @Test
  void unchangedQuoteSuppressesRuleAndGlobalNotification() {
    ExchangeRateAlertRule rule = rule("BELOW", "158", "r158");
    when(settingsMapper.selectById("DEFAULT")).thenReturn(settings());
    when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
    when(pairStateMapper.selectOne(any())).thenReturn(pairState("155"));
    when(exchangeRateService.realtime("USD", "JPY")).thenReturn(quote("155"));

    service.evaluateEnabledRules();

    verify(barkNotificationService, never()).send(anyString(), any());
    assertEquals(new BigDecimal("155"), rule.getLastObservedRate());
  }

  @Test
  void belowRulesUseNearestUpperThresholdNaturalSegment() {
    ExchangeRateAlertRule r150 = rule("BELOW", "150", "r150");
    r150.setBarkLevel("critical");
    ExchangeRateAlertRule r158 = rule("BELOW", "158", "r158");
    r158.setBarkLevel("active");

    when(settingsMapper.selectById("DEFAULT")).thenReturn(settings());
    when(ruleMapper.selectList(any())).thenReturn(List.of(r150, r158));
    when(pairStateMapper.selectOne(any())).thenReturn(pairState("151"));
    when(exchangeRateService.realtime("USD", "JPY")).thenReturn(quote("149"));
    when(barkNotificationService.send(anyString(), any())).thenReturn(true);

    service.evaluateEnabledRules();

    ArgumentCaptor<BarkNotificationVo> notification =
        ArgumentCaptor.forClass(BarkNotificationVo.class);
    verify(barkNotificationService).send(anyString(), notification.capture());
    assertEquals("critical", notification.getValue().level());
    assertTrue(r150.getLastTriggeredAt() != null);
    assertEquals(null, r158.getLastTriggeredAt());
  }

  @Test
  void secondBelowSegmentWinsForRateBetween150And158() {
    ExchangeRateAlertRule r150 = rule("BELOW", "150", "r150");
    r150.setBarkLevel("critical");
    ExchangeRateAlertRule r158 = rule("BELOW", "158", "r158");
    r158.setBarkLevel("active");

    when(settingsMapper.selectById("DEFAULT")).thenReturn(settings());
    when(ruleMapper.selectList(any())).thenReturn(List.of(r150, r158));
    when(pairStateMapper.selectOne(any())).thenReturn(pairState("154"));
    when(exchangeRateService.realtime("USD", "JPY")).thenReturn(quote("155"));
    when(barkNotificationService.send(anyString(), any())).thenReturn(true);

    service.evaluateEnabledRules();

    ArgumentCaptor<BarkNotificationVo> notification =
        ArgumentCaptor.forClass(BarkNotificationVo.class);
    verify(barkNotificationService).send(anyString(), notification.capture());
    assertEquals("active", notification.getValue().level());
    assertTrue(r158.getLastTriggeredAt() != null);
  }

  @Test
  void noSpecialRuleUsesGlobalNotificationPolicy() {
    ExchangeRateAlertRule below158 = rule("BELOW", "158", "r158");
    ExchangeRateAlertRule above160 = rule("ABOVE", "160", "r160");

    when(settingsMapper.selectById("DEFAULT")).thenReturn(settings());
    when(ruleMapper.selectList(any())).thenReturn(List.of(below158, above160));
    when(pairStateMapper.selectOne(any())).thenReturn(pairState("158.5"));
    when(exchangeRateService.realtime("USD", "JPY")).thenReturn(quote("159"));
    when(barkNotificationService.send(anyString(), any())).thenReturn(true);

    service.evaluateEnabledRules();

    ArgumentCaptor<BarkNotificationVo> notification =
        ArgumentCaptor.forClass(BarkNotificationVo.class);
    verify(barkNotificationService).send(anyString(), notification.capture());
    assertEquals("active", notification.getValue().level());
    assertTrue(notification.getValue().title().contains("USD/JPY"));
  }

  @Test
  void crossEventWinsBeforeStateRule() {
    ExchangeRateAlertRule cross = rule("CROSS_BELOW", "150", "cross");
    cross.setLastObservedRate(new BigDecimal("151"));
    cross.setBarkLevel("critical");
    ExchangeRateAlertRule below158 = rule("BELOW", "158", "below");

    when(settingsMapper.selectById("DEFAULT")).thenReturn(settings());
    when(ruleMapper.selectList(any())).thenReturn(List.of(cross, below158));
    when(pairStateMapper.selectOne(any())).thenReturn(pairState("151"));
    when(exchangeRateService.realtime("USD", "JPY")).thenReturn(quote("149"));
    when(barkNotificationService.send(anyString(), any())).thenReturn(true);

    service.evaluateEnabledRules();

    ArgumentCaptor<BarkNotificationVo> notification =
        ArgumentCaptor.forClass(BarkNotificationVo.class);
    verify(barkNotificationService).send(anyString(), notification.capture());
    assertEquals("critical", notification.getValue().level());
    assertTrue(cross.getLastTriggeredAt() != null);
    assertEquals(null, below158.getLastTriggeredAt());
  }

  @Test
  void belowRuleCanUseDynamicSystemWeightedAverageCostAsThreshold() {
    ExchangeRateAlertRule rule = rule("BELOW", "1", "avg-cost");
    rule.setThresholdModeCode("AVERAGE_COST");
    rule.setThresholdRate(null);
    rule.setBarkLevel("critical");

    when(settingsMapper.selectById("DEFAULT")).thenReturn(settings());
    when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
    when(pairStateMapper.selectOne(any())).thenReturn(pairState("152"));
    when(exchangeRateService.realtime("USD", "JPY")).thenReturn(quote("149"));
    when(fxPositionMapper.selectSystemWeightedAverageRate("USD", "JPY"))
        .thenReturn(new BigDecimal("150.5000000000"));
    when(barkNotificationService.send(anyString(), any())).thenReturn(true);

    service.evaluateEnabledRules();

    ArgumentCaptor<BarkNotificationVo> notification =
        ArgumentCaptor.forClass(BarkNotificationVo.class);
    verify(barkNotificationService).send(anyString(), notification.capture());
    assertEquals("critical", notification.getValue().level());
    assertTrue(rule.getLastTriggeredAt() != null);
  }

  @Test
  void averageCostThresholdUsesV3BucketsAfterCutover() {
    ExchangeRateAlertRule rule = rule("BELOW", "1", "avg-cost-v3");
    rule.setThresholdModeCode("AVERAGE_COST");
    rule.setThresholdRate(null);

    when(settingsMapper.selectById("DEFAULT")).thenReturn(settings());
    when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
    when(pairStateMapper.selectOne(any())).thenReturn(pairState("152"));
    when(exchangeRateService.realtime("USD", "JPY")).thenReturn(quote("149"));
    when(currencyInventoryRebuildMapper.selectInventoryCutoverAppliedAt())
        .thenReturn(Instant.parse("2026-08-18T00:00:00Z"));
    when(currencyInventoryBucketMapper.selectSystemWeightedAverageRate("USD", "JPY"))
        .thenReturn(new BigDecimal("150.5000000000"));
    when(barkNotificationService.send(anyString(), any())).thenReturn(true);

    service.evaluateEnabledRules();

    verify(currencyInventoryBucketMapper).selectSystemWeightedAverageRate("USD", "JPY");
    verify(fxPositionMapper, never()).selectSystemWeightedAverageRate("USD", "JPY");
    assertTrue(rule.getLastTriggeredAt() != null);
  }

  @Test
  void averageCostThresholdWithoutPositionFallsBackToGlobalPolicy() {
    ExchangeRateAlertRule rule = rule("BELOW", "1", "avg-cost");
    rule.setThresholdModeCode("AVERAGE_COST");
    rule.setThresholdRate(null);

    when(settingsMapper.selectById("DEFAULT")).thenReturn(settings());
    when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
    when(pairStateMapper.selectOne(any())).thenReturn(pairState("152"));
    when(exchangeRateService.realtime("USD", "JPY")).thenReturn(quote("149"));
    when(fxPositionMapper.selectSystemWeightedAverageRate("USD", "JPY")).thenReturn(null);
    when(barkNotificationService.send(anyString(), any())).thenReturn(true);

    service.evaluateEnabledRules();

    ArgumentCaptor<BarkNotificationVo> notification =
        ArgumentCaptor.forClass(BarkNotificationVo.class);
    verify(barkNotificationService).send(anyString(), notification.capture());
    assertTrue(notification.getValue().title().contains("USD/JPY"));
    assertEquals(null, rule.getLastTriggeredAt());
  }

  @Test
  void notificationTemplateCanUseSystemWeightedAverageCostRate() {
    ExchangeRateAlertRule rule = rule("BELOW", "158", "r158");
    rule.setNotificationBodyTemplate(
        "当前：{currentRate}\n成本：{averageCostRate}\n差额：{round(currentRate - averageCostRate, 4)}");

    when(settingsMapper.selectById("DEFAULT")).thenReturn(settings());
    when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
    when(pairStateMapper.selectOne(any())).thenReturn(pairState("156"));
    when(exchangeRateService.realtime("USD", "JPY")).thenReturn(quote("155"));
    when(fxPositionMapper.selectSystemWeightedAverageRate("USD", "JPY"))
        .thenReturn(new BigDecimal("151.3333333333"));
    when(barkNotificationService.send(anyString(), any())).thenReturn(true);

    service.evaluateEnabledRules();

    ArgumentCaptor<BarkNotificationVo> notification =
        ArgumentCaptor.forClass(BarkNotificationVo.class);
    verify(barkNotificationService).send(anyString(), notification.capture());
    assertTrue(notification.getValue().body().contains("成本：151.3333333333"));
    assertTrue(notification.getValue().body().contains("差额：3.6667"));
  }

  @Test
  void masterSwitchStopsAllWork() {
    ExchangeRateAlertSettings settings = settings();
    settings.setMasterEnabled(false);
    when(settingsMapper.selectById("DEFAULT")).thenReturn(settings);

    service.evaluateEnabledRules();

    verify(ruleMapper, never()).selectList(any());
    verify(exchangeRateService, never()).realtime(anyString(), anyString());
  }

  private ExchangeRateAlertSettings settings() {
    ExchangeRateAlertSettings settings = new ExchangeRateAlertSettings();
    settings.setSettingsId("DEFAULT");
    settings.setMasterEnabled(true);
    settings.setPollingIntervalSeconds(60);
    settings.setDefaultCooldownMinutes(60);
    settings.setDefaultRateSourceCode("TRADINGVIEW");
    settings.setDefaultChannelCode("BARK");
    settings.setGlobalNotificationEnabled(true);
    settings.setGlobalNotificationTitle("{baseCurrency}/{quoteCurrency} 汇率通知");
    settings.setGlobalNotificationBodyTemplate("{currentRate}");
    settings.setBarkBaseUrl("https://api.day.app/push");
    settings.setBarkLevel("active");
    settings.setBarkGroupName("外汇助手");
    return settings;
  }

  private ExchangeRateAlertRule rule(String operator, String threshold, String id) {
    ExchangeRateAlertRule rule = new ExchangeRateAlertRule();
    rule.setAlertRuleId(id);
    rule.setRuleName("USDJPY " + id);
    rule.setBaseCurrency("USD");
    rule.setQuoteCurrency("JPY");
    rule.setOperatorCode(operator);
    rule.setThresholdModeCode("FIXED");
    rule.setThresholdRate(new BigDecimal(threshold));
    rule.setEnabled(true);
    return rule;
  }

  private ExchangeRateAlertPairState pairState(String lastObserved) {
    ExchangeRateAlertPairState state = new ExchangeRateAlertPairState();
    state.setPairStateId("ps1");
    state.setBaseCurrency("USD");
    state.setQuoteCurrency("JPY");
    state.setRateSourceCode("TRADINGVIEW");
    state.setLastObservedRate(lastObserved == null ? null : new BigDecimal(lastObserved));
    state.setCreatedAt(Instant.now());
    state.setUpdatedAt(Instant.now());
    return state;
  }

  private RealtimeExchangeRateVo quote(String rate) {
    return new RealtimeExchangeRateVo(
        "USD", "JPY", new BigDecimal(rate), "TRADINGVIEW", Instant.now(), false);
  }
}
