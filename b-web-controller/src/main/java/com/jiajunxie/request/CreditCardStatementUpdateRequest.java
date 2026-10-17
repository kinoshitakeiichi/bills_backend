package com.jiajunxie.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

@Data
public class CreditCardStatementUpdateRequest {
  @NotNull private LocalDate periodStart;
  @NotNull private LocalDate periodEnd;
  private LocalDate confirmationDate;
  @NotNull private LocalDate scheduledPaymentDate;
  @NotNull private LocalDate calculatedPaymentDate;
  private LocalDate manualPaymentDate;
}
