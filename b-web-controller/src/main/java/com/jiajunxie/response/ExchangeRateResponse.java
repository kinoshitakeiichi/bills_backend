package com.jiajunxie.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;

@Data
public class ExchangeRateResponse {
  private String rateId;
  private LocalDate rateDate;
  private String baseCurrency;
  private String quoteCurrency;
  private BigDecimal exchangeRate;
  private String rateSourceCode;
  private Instant createdAt;
  private boolean derived;
}
