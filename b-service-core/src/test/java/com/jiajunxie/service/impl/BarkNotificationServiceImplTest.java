package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiajunxie.vo.BarkNotificationVo;
import org.junit.jupiter.api.Test;

class BarkNotificationServiceImplTest {

  @Test
  void blankDeviceKeyIsReportedAsNotConfiguredAndSendIsSkipped() {
    BarkNotificationServiceImpl service = new BarkNotificationServiceImpl(" ");

    assertFalse(service.isConfigured());
    assertFalse(
        service.send(
            "https://api.day.app/push",
            BarkNotificationVo.builder().title("test").body("body").build()));
  }

  @Test
  void nonBlankDeviceKeyIsReportedAsConfigured() {
    BarkNotificationServiceImpl service = new BarkNotificationServiceImpl("secret");

    assertTrue(service.isConfigured());
  }
}
