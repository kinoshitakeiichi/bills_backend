package com.jiajunxie.vo;

import java.time.LocalDate;

public record ReminderDatePreviewVo(
    LocalDate scheduledDate,
    LocalDate calculatedDate,
    String dateRuleSourceCode,
    String invalidDayRuleCode,
    String calendarCode,
    String businessDayAdjustmentCode,
    String timezone,
    String sourceDescription) {}
