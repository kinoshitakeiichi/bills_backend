package com.jiajunxie.response;

import java.time.LocalDate;
import lombok.Data;

@Data
public class CreditCardPaymentOptionResponse {
  private String paymentTypeCode;
  private String ruleId;
  private Integer priority;
  private LocalDate scheduledPaymentDate;
  private LocalDate calculatedPaymentDate;
}
