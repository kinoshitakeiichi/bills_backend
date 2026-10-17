package com.jiajunxie.response;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

@Data
public class RealtimeExchangeRateResponse {
  private String baseCurrency;
  private String quoteCurrency;
  private BigDecimal exchangeRate;
  private String rateSourceCode;
  private Instant observedAt;
  private boolean derived;
}
