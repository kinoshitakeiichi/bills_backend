package com.jiajunxie.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AccountRequest {

  private String accountId;

  @NotBlank(message = "账户名称不能为空")
  private String accountName;

  @NotBlank(message = "账户编码不能为空")
  private String accountCode;

  private String issuer;

  // Display-only compatibility field. AccountService derives the authoritative class from master data.
  private String accountClassCode;

  @NotBlank(message = "账户类型不能为空")
  private String accountTypeCode;

  @NotBlank(message = "币种不能为空")
  private String currency;

  @NotBlank(message = "账户归属人不能为空")
  private String ownerPersonId;

  private String parentAccountId;
  private String autoDebitAccountId;
}
