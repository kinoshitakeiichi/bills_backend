package com.jiajunxie.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BusinessCalendarDayRequest {
  @NotNull(message = "营业日状态不能为空") private Boolean businessDay;
  @Size(max = 100) private String holidayName;
  @Size(max = 50) private String source;
}
