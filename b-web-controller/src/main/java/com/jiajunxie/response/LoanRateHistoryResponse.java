package com.jiajunxie.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;

@Data
public class LoanRateHistoryResponse {
  private String rateHistoryId;
  private String accountId;
  private LocalDate effectiveDate;
  private BigDecimal annualRate;
  private Instant createdAt;
}
