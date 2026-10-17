package com.jiajunxie.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import jakarta.validation.Valid;
import lombok.Data;

@Data
public class LoanPurchaseRequest {
  @NotBlank private String loanAccountId;
  @NotBlank private String categoryId;
  @NotNull @DecimalMin(value="0", inclusive=false) private BigDecimal amount;
  @NotBlank private String currency;
  private String merchant;
  private String remark;
  private Instant transactionTime;
  private List<@Valid TransactionAllocationRequest> allocations;
}
