package com.jiajunxie.model;

import java.time.Instant;
import lombok.Data;

@Data
public class TransactionLedgerCandidate {
  private String transactionId;
  private String transactionTypeCode;
  private String transactionTypeName;
  private String categoryId;
  private String categoryName;
  private String merchant;
  private String remark;
  private Instant transactionTime;
  private String originCode;
  private String originReferenceId;
  private Instant createdAt;
  private String fxDirectionCode;
  private java.math.BigDecimal fxFeeAmount;
  private String fxFeeCurrency;
}
