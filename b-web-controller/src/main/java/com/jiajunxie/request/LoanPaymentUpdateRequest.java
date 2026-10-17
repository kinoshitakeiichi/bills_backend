package com.jiajunxie.request;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.Data;

@Data
public class LoanPaymentUpdateRequest {
  @NotBlank private String sourceAccountId;
  private String remark;
  private Instant transactionTime;
}
