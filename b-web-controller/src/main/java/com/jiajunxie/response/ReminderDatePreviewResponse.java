package com.jiajunxie.response;

import java.time.LocalDate;

public record ReminderDatePreviewResponse(
    LocalDate scheduledDate,
    LocalDate calculatedDate,
    String dateRuleSourceCode,
    String invalidDayRuleCode,
    String calendarCode,
    String businessDayAdjustmentCode,
    String timezone,
    String sourceDescription) {}
