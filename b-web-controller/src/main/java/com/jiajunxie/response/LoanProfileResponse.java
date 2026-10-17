package com.jiajunxie.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;

@Data
public class LoanProfileResponse {
  private String accountId;
  private String loanTypeCode;
  private String fundingModeCode;
  private BigDecimal originalPrincipal;
  private String currency;
  private String rateTypeCode;
  private BigDecimal annualRate;
  private LocalDate startDate;
  private LocalDate maturityDate;
  private String paymentFrequencyCode;
  private Integer paymentDay;
  private String calendarCode;
  private String businessDayAdjustmentCode;
  private Instant createdAt;
  private Instant updatedAt;
}
