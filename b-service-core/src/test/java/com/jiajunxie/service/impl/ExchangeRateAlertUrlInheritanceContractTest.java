package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExchangeRateAlertUrlInheritanceContractTest {

  @Test
  void ruleUrlFallsBackToGlobalThenTradingView() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/jiajunxie/service/impl/ExchangeRateAlertServiceImpl.java"));
    assertTrue(source.contains("effectiveNotificationUrl("));
    assertTrue(source.contains("rule.getNotificationUrl()"));
    assertTrue(source.contains("settings.getGlobalNotificationUrl()"));
    assertTrue(source.contains("return tradingViewUrl(base, quote, source);"));
  }
}
