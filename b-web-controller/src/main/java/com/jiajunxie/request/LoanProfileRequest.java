package com.jiajunxie.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class LoanProfileRequest {
  @NotBlank @Size(max = 30) private String loanTypeCode;
  @NotBlank @Size(max = 30) private String fundingModeCode;
  @DecimalMin(value = "0.0", inclusive = false) private BigDecimal originalPrincipal;
  @Size(max = 10) private String currency;
  @NotBlank @Size(max = 30) private String rateTypeCode;
  @NotNull @DecimalMin(value = "0.0", inclusive = true) private BigDecimal annualRate;
  @NotNull private LocalDate startDate;
  private LocalDate maturityDate;
  @NotBlank @Size(max = 30) private String paymentFrequencyCode;
  @Min(1) @Max(31) private Integer paymentDay;
  @Size(max = 50) private String calendarCode;
  @Size(max = 30) private String businessDayAdjustmentCode;
}
