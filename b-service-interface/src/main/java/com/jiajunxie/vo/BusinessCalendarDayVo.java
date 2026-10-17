package com.jiajunxie.vo;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;

@Data
public class BusinessCalendarDayVo {
  private String calendarDayId;
  private String calendarId;
  private LocalDate calendarDate;
  private Boolean businessDay;
  private String holidayName;
  private String source;
  private Instant createdAt;
  private Instant updatedAt;
}
