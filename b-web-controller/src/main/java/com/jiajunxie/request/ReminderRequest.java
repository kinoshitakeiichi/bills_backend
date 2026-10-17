package com.jiajunxie.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReminderRequest(
    @NotBlank @Size(max = 100) String reminderName,
    @NotBlank String reminderTypeCode,
    String accountId,
    @NotBlank String scheduleModeCode,
    LocalDate remindDate,
    Integer dayOfMonth,
    @NotNull LocalTime remindTime,
    String timezone,
    String dateRuleSourceCode,
    String invalidDayRuleCode,
    String calendarCode,
    String businessDayAdjustmentCode,
    @Size(max = 1000) String notificationUrl,
    @Size(max = 200) String notificationTitle,
    @Size(max = 1000) String notificationBody,
    Boolean enabled) {}
