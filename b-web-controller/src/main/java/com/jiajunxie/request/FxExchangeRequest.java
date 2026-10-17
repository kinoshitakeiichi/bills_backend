package com.jiajunxie.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

@Data
public class FxExchangeRequest {

  @NotBlank(message = "FX 方向不能为空")
  private String fxDirectionCode;

  @NotBlank(message = "来源账户不能为空")
  private String sourceAccountId;

  @NotBlank(message = "目标账户不能为空")
  private String targetAccountId;

  @NotNull(message = "来源金额不能为空")
  @DecimalMin(value = "0", inclusive = false, message = "来源金额必须大于 0")
  private BigDecimal sourceAmount;

  @NotBlank(message = "来源币种不能为空")
  private String sourceCurrency;

  @NotNull(message = "目标金额不能为空")
  @DecimalMin(value = "0", inclusive = false, message = "目标金额必须大于 0")
  private BigDecimal targetAmount;

  @NotBlank(message = "目标币种不能为空")
  private String targetCurrency;

  /** Compatibility field. The service derives the accounting rate from targetAmount/sourceAmount. */
  private BigDecimal executedRate;

  private BigDecimal feeAmount;
  private String feeCurrency;
  private String remark;
  private Instant transactionTime;
}
