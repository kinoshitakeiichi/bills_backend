package com.jiajunxie.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;

@Data
public class CreditRefundScheduleChange {
  private String changeId;
  private String refundTransactionId;
  private String purchaseTransactionId;
  private String scheduleId;
  private Integer changeOrder;
  private BigDecimal principalReduction;
  private Boolean scheduleDeleted;
  private String installmentPlanId;
  private String liabilityAccountId;
  private Integer installmentNo;
  private Integer installmentCount;
  private BigDecimal principalBefore;
  private BigDecimal feeBefore;
  private BigDecimal adjustmentBefore;
  private String currency;
  private String paymentTypeCode;
  private LocalDate scheduledBillingDate;
  private LocalDate scheduledPaymentDate;
  private LocalDate calculatedPaymentDate;
  private LocalDate manualPaymentDate;
  private String statusCode;
  private Instant scheduleCreatedAt;
  private Instant createdAt;
}
