package com.jiajunxie.vo;

import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class CreditCardDatePreviewVo {
  private LocalDate referenceDate;
  private LocalDate periodStart;
  private LocalDate periodEnd;
  private LocalDate confirmationDate;

  /** NORMAL payment result calculated only from CreditCardProfile. */
  private LocalDate scheduledPaymentDate;
  private LocalDate calculatedPaymentDate;

  /**
   * All payment choices valid for the reference date.
   *
   * <p>Matching special rules are ordered by priority descending. NORMAL is always appended as
   * the fallback option with a null priority.
   */
  private List<CreditCardPaymentOptionVo> paymentOptions;
}
