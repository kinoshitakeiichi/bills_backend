package com.jiajunxie.request;

import java.time.Instant;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransactionRequest {

  @NotBlank(message = "交易类型不能为空")
  private String transactionType;

  private String categoryId;
  private String merchant;
  private String remark;

  @NotNull(message = "交易时间不能为空")
  private Instant transactionTime;

  @NotEmpty(message = "交易至少需要一条分录")
  private List<@Valid TransactionEntryRequest> entries;

  private List<@Valid TransactionAllocationRequest> allocations;

  @Valid
  private TransactionPaymentRequest payment;

  private List<@Valid StatementPaymentAllocationRequest> paymentAllocations;
}
