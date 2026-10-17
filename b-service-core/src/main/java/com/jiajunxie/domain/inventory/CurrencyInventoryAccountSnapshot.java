package com.jiajunxie.domain.inventory;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class CurrencyInventoryAccountSnapshot {
  private String accountId;
  private String accountName;
  private String currency;
  private BigDecimal accountBalance;
  private BigDecimal ledgerBalance;
}
