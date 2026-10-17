package com.jiajunxie.domain.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanScheduleGenerationResult(
    String installmentPlanId,
    String sourceTransactionId,
    String loanAccountId,
    int installmentCount,
    BigDecimal principalAmount,
    BigDecimal totalInterestAmount,
    BigDecimal totalFeeAmount,
    String currency,
    LocalDate firstCalculatedPaymentDate,
    String calculationVersion) {}
