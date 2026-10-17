package com.jiajunxie.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class CreditCardProfileRequest {
  @DecimalMin(value = "0.0", inclusive = true, message = "信用额度不能为负数") private BigDecimal creditLimit;
  @NotNull @Min(value = 0, message = "金额小数位不能小于 0")
  @Max(value = 4, message = "金额小数位不能大于 4")
  private Integer amountScale;
  @NotNull @Min(1) @Max(31) private Integer billingStartDay;
  @NotNull @Min(1) @Max(31) private Integer billingEndDay;
  @NotNull @Min(0) @Max(3) private Integer billingEndMonthOffset;
  @Min(1) @Max(31) private Integer confirmationDay;
  @NotNull @Min(0) @Max(6) private Integer confirmationMonthOffset;
  @NotNull @Min(1) @Max(31) private Integer paymentDay;
  @NotNull @Min(0) @Max(12) private Integer paymentMonthOffset;
  @NotBlank @Size(max = 30) private String invalidDayRuleCode;
  @NotBlank @Size(max = 30) private String businessDayAdjustmentCode;
  @Size(max = 50) private String calendarCode;
  @NotBlank @Size(max = 50) private String timezone;
  @NotBlank @Size(max = 30) private String remainderRuleCode;
  private Boolean enabled;
}
