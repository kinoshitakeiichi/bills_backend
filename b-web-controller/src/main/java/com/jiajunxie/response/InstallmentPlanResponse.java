package com.jiajunxie.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InstallmentPlanResponse(
    String installmentPlanId,
    String paymentTypeCode,
    String paymentRuleId,
    BigDecimal principalAmount,
    BigDecimal feeAmount,
    String currency,
    Integer installmentCount,
    LocalDate firstDueDate,
    String remainderRuleCode,
    String statusCode,
    List<PaymentScheduleResponse> schedules) {}
