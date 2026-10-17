package com.jiajunxie.vo;

import java.time.LocalDate;
import lombok.Data;

/**
 * One selectable payment-date result produced by the credit-card rule preview.
 *
 * <p>Special payment rules are recommendations/available choices only. They never silently
 * replace the card profile's NORMAL payment result.
 */
@Data
public class CreditCardPaymentOptionVo {
  private String paymentTypeCode;
  private String ruleId;
  private Integer priority;
  private LocalDate scheduledPaymentDate;
  private LocalDate calculatedPaymentDate;
}
