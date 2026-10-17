package com.jiajunxie.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class StatementAdjustmentRequest {
  private String adjustmentTypeCode;
  private BigDecimal amount;
  private String currency;
  private LocalDate oldPaymentDate;
  private LocalDate newPaymentDate;
  private String reason;
}
