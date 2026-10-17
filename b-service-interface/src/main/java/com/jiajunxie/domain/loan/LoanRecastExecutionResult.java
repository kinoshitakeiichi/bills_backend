package com.jiajunxie.domain.loan;

import java.math.BigDecimal;

public record LoanRecastExecutionResult(
    String installmentPlanId,
    int fromInstallmentNo,
    LoanRecastStrategy strategy,
    int generationNo,
    int cancelledScheduleCount,
    int createdScheduleCount,
    int newInstallmentCount,
    BigDecimal remainingPrincipal,
    BigDecimal firstNewPaymentAmount,
    String calculationVersion) {}
