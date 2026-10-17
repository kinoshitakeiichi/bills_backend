package com.jiajunxie.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreditCardStatementReopenRequest {

  @NotBlank(message = "撤销确认原因不能为空")
  private String reason;
}
