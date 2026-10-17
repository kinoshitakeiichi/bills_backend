package com.jiajunxie.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.time.Instant;
import lombok.Data;

@Data
public class LoanPaymentRequest {
  @NotBlank private String loanAccountId;
  @NotBlank private String sourceAccountId;
  private List<String> scheduleIds;

  @NotNull
  @DecimalMin(value = "0", inclusive = true)
  private BigDecimal principalAmount;

  @NotNull
  @DecimalMin(value = "0", inclusive = true)
  private BigDecimal interestAmount;

  @NotNull
  @DecimalMin(value = "0", inclusive = true)
  private BigDecimal feeAmount;

  @NotBlank private String currency;
  private String remark;
  private Instant transactionTime;
}
