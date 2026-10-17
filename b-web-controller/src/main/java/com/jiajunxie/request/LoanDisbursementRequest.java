package com.jiajunxie.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

@Data
public class LoanDisbursementRequest {
  @NotBlank(message = "贷款账户不能为空")
  private String loanAccountId;

  @NotBlank(message = "入账账户不能为空")
  private String targetAccountId;

  @NotNull(message = "放款金额不能为空")
  @DecimalMin(value = "0", inclusive = false, message = "放款金额必须大于 0")
  private BigDecimal amount;

  @NotBlank(message = "币种不能为空")
  private String currency;

  private String remark;
  private Instant transactionTime;
}
