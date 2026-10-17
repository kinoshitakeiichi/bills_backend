package com.jiajunxie.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;

@Data
public class FxPositionValuationResponse {
  private String positionId;
  private String accountId;
  private String currency;
  private BigDecimal quantity;
  private BigDecimal totalCost;
  private String costCurrency;
  private BigDecimal averageCostRate;
  private String valuationMode;
  private LocalDate valuationDate;
  private BigDecimal effectiveRate;
  private String rateSourceCode;
  private LocalDate rateDate;
  private Instant observedAt;
  private boolean derivedRate;
  private BigDecimal marketValue;
  private BigDecimal unrealizedGainLoss;
  private BigDecimal unrealizedReturnRate;
}
