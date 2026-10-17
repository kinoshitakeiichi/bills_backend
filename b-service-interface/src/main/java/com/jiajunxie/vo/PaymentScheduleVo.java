package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentScheduleVo(
    String scheduleId,
    Integer installmentNo,
    Integer installmentCount,
    BigDecimal scheduledPrincipalAmount,
    BigDecimal scheduledFeeAmount,
    BigDecimal adjustmentAmount,
    String currency,
    String paymentTypeCode,
    LocalDate scheduledBillingDate,
    LocalDate scheduledPaymentDate,
    LocalDate calculatedPaymentDate,
    LocalDate manualPaymentDate,
    String statusCode) {}
