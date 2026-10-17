package com.jiajunxie.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreditPaymentRuleRequest {
  @NotBlank @Size(max = 30) private String ruleTypeCode;
  @NotBlank @Pattern(regexp = "\\d{2}-\\d{2}", message = "购买区间开始日期格式必须为 MM-DD") private String purchaseStartMonthDay;
  @NotBlank @Pattern(regexp = "\\d{2}-\\d{2}", message = "购买区间结束日期格式必须为 MM-DD") private String purchaseEndMonthDay;
  @NotNull @Min(1) @Max(12) private Integer targetMonth;
  @NotNull @Min(1) @Max(31) private Integer targetDay;
  @NotNull @Min(0) private Integer priority;
  private Boolean enabled;
}
