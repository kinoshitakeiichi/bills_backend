package com.jiajunxie.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BusinessCalendarRequest {
  @NotBlank(message = "日历编码不能为空") @Size(max = 50) private String calendarCode;
  @NotBlank(message = "日历名称不能为空") @Size(max = 100) private String calendarName;
  @Size(max = 10) private String countryCode;
  private Boolean enabled;
}
