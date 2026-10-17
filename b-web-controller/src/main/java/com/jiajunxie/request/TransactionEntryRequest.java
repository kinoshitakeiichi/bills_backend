package com.jiajunxie.request;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransactionEntryRequest {

  @NotBlank(message = "分录账户 ID 不能为空")
  private String accountId;

  @NotNull(message = "分录金额不能为空")
  private BigDecimal amount;

  @NotBlank(message = "分录币种不能为空")
  private String currency;

  @NotBlank(message = "分录角色不能为空")
  private String entryRole;
}
