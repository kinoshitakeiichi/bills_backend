package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;

@Data
public class LoanRateHistoryVo {
  private String rateHistoryId;
  private String accountId;
  private LocalDate effectiveDate;
  private BigDecimal annualRate;
  private Instant createdAt;
}
