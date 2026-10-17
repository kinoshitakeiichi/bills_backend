package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForexMonitorRetirementContractTest {

  @Test
  void legacyMonitorNoLongerOwnsSchedulerThresholdsOrBarkSecret() throws Exception {
    Path source =
        Path.of(
            "src/main/java/com/jiajunxie/service/impl/ForexMonitorServiceImpl.java");
    String text = Files.readString(source);

    assertFalse(text.contains("@Scheduled"));
    assertFalse(text.contains("BARK_DEVICE_KEY"));
    assertFalse(text.contains("HIGH_RATE"));
    assertFalse(text.contains("MEDIUM_RATE"));
    assertTrue(text.contains("TradingViewRateProvider"));
    assertTrue(text.contains("BarkNotificationService"));
  }
}
