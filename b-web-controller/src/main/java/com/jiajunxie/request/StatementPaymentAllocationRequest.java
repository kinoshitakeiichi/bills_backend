package com.jiajunxie.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class StatementPaymentAllocationRequest {

  @NotBlank(message = "账单 ID 不能为空")
  private String statementId;

  @NotNull(message = "分配金额不能为空")
  @DecimalMin(value = "0.0", inclusive = false, message = "分配金额必须大于 0")
  private BigDecimal allocatedAmount;

  private Integer allocationOrder;
}
