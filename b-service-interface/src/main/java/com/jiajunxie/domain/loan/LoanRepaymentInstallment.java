package com.jiajunxie.domain.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanRepaymentInstallment(
    int installmentNo,
    BigDecimal principalAmount,
    BigDecimal interestAmount,
    BigDecimal feeAmount,
    BigDecimal totalAmount,
    BigDecimal remainingPrincipalAfter,
    BigDecimal annualRateSnapshot,
    LocalDate rateEffectiveFrom) {}
