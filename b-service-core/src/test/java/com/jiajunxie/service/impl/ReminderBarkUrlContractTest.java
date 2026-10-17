package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReminderBarkUrlContractTest {

  @Test
  void reminderServicePassesConfiguredUrlToBarkNotification() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/jiajunxie/service/impl/ReminderServiceImpl.java"));
    assertTrue(source.contains(".url(reminder.getNotificationUrl())"));
  }
}
