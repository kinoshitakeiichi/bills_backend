package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InstallmentPlanVo(
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
    List<PaymentScheduleVo> schedules) {}
