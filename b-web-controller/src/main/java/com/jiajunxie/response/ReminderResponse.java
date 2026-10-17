package com.jiajunxie.response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReminderResponse(
    String reminderId,
    String reminderName,
    String reminderTypeCode,
    String accountId,
    String scheduleModeCode,
    LocalDate remindDate,
    Integer dayOfMonth,
    LocalTime remindTime,
    String timezone,
    String dateRuleSourceCode,
    String invalidDayRuleCode,
    String calendarCode,
    String businessDayAdjustmentCode,
    String notificationUrl,
    String notificationTitle,
    String notificationBody,
    Boolean enabled,
    Instant lastTriggeredAt,
    Instant createdAt,
    Instant updatedAt) {}
