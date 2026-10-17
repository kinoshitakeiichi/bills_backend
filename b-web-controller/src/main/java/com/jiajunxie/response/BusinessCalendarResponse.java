package com.jiajunxie.response;

import lombok.Data;

@Data
public class BusinessCalendarResponse {
  private String calendarId;
  private String calendarCode;
  private String calendarName;
  private String countryCode;
  private Boolean enabled;
}
