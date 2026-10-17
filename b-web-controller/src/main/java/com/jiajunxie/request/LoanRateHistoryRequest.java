package com.jiajunxie.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class LoanRateHistoryRequest {
  @NotNull private LocalDate effectiveDate;
  @NotNull @DecimalMin(value = "0.0", inclusive = true) private BigDecimal annualRate;
}
