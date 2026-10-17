package com.jiajunxie.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoanPurchaseAdoptRequest {
  @NotBlank
  private String loanAccountId;
}
