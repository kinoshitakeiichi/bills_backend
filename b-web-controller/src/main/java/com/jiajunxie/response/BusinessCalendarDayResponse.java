package com.jiajunxie.response;

import java.time.LocalDate;
import lombok.Data;

@Data
public class BusinessCalendarDayResponse {
  private String calendarDayId;
  private String calendarId;
  private LocalDate calendarDate;
  private Boolean businessDay;
  private String holidayName;
  private String source;
}
