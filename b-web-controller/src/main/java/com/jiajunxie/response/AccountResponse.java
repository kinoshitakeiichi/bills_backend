package com.jiajunxie.response;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class AccountResponse {

  private String accountId;

  private String accountName;

  private String accountCode;

  private String issuer;

  private String accountClassCode;

  /** Human-readable account class name resolved from master data. */
  private String accountClassName;

  private String accountTypeCode;

  /** Human-readable account type name resolved from master data. */
  private String accountTypeName;

  private BigDecimal totalAmount;

  private String currency;

  /** Stable Person relationship key used when editing the account. */
  private String ownerPersonId;

  /** Human-readable Person name used by cards and other read-only views. */
  private String ownerPersonName;

  private String parentAccountId;

  private String autoDebitAccountId;

  private Boolean enabled;
}
