package com.jiajunxie.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class TransactionAllocationRequest {
  @NotBlank(message = "人员不能为空")
  private String personId;

  @NotNull(message = "分摊金额不能为空")
  @DecimalMin(value = "0.0", inclusive = true, message = "分摊金额不能小于零")
  private BigDecimal allocatedAmount;

  @NotBlank(message = "分摊币种不能为空")
  private String currency;

  private BigDecimal allocationRatio;
}
