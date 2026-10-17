package com.jiajunxie.model;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

/** 账户详情查询使用的数据库投影，不作为 JPA 实体。 */
@Data
public class AccountTransactionRow {
  private String transactionId;
  private String transactionType;
  private String transactionTypeName;
  private String categoryId;
  private String categoryName;
  private String merchant;
  private String remark;
  private Instant transactionTime;
  private BigDecimal amount;
  private String currency;
  private String entryRole;
  private String originCode;
  private String originReferenceId;
  private String fxDirectionCode;
  private String fxSourceAccountId;
  private String fxSourceAccountName;
  private BigDecimal fxSourceAmount;
  private String fxSourceCurrency;
  private String fxTargetAccountId;
  private String fxTargetAccountName;
  private BigDecimal fxTargetAmount;
  private String fxTargetCurrency;
  private BigDecimal fxExecutedRate;
  private BigDecimal fxFeeAmount;
  private String fxFeeCurrency;
  private BigDecimal fxCostBasisAmount;
  private BigDecimal fxRealizedGainLoss;
  private String fxGainLossCurrency;
  private BigDecimal loanPrincipalAmount;
  private BigDecimal loanInterestAmount;
  private BigDecimal loanFeeAmount;
  private BigDecimal loanTotalPaymentAmount;
  private String loanSourceAccountId;
  private String loanSourceAccountName;
}
