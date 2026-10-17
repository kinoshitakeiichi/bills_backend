package com.jiajunxie.response;

import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class CreditCardDatePreviewResponse {
  private LocalDate referenceDate;
  private LocalDate periodStart;
  private LocalDate periodEnd;
  private LocalDate confirmationDate;
  private LocalDate scheduledPaymentDate;
  private LocalDate calculatedPaymentDate;
  private List<CreditCardPaymentOptionResponse> paymentOptions;
}
