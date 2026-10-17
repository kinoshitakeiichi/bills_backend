package com.jiajunxie.vo;

import java.time.Instant;
import lombok.Data;

@Data
public class BusinessCalendarVo {
  private String calendarId;
  private String calendarCode;
  private String calendarName;
  private String countryCode;
  private Boolean enabled;
  private Instant createdAt;
  private Instant updatedAt;
}
