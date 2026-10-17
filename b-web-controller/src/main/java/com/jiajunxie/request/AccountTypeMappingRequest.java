package com.jiajunxie.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AccountTypeMappingRequest {
  @NotBlank(message = "账户类别编码不能为空")
  private String accountClassCode;
}
