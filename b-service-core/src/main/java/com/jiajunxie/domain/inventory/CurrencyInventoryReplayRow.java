package com.jiajunxie.domain.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

@Data
public class CurrencyInventoryReplayRow {
  private String transactionId;
  private String transactionType;
  private String categoryCode;
  private Instant transactionTime;
  private Instant transactionCreatedAt;
  private String transactionRemark;

  private String entryId;
  private String accountId;
  private BigDecimal amount;
  private String entryCurrency;
  private String entryRole;
  private Instant entryCreatedAt;

  private String accountName;
  private String accountCurrency;
  private Boolean legacyOpeningBalance;
}
