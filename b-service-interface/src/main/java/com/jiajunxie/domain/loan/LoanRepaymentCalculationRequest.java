package com.jiajunxie.domain.loan;

import java.math.BigDecimal;
import java.util.List;

public record LoanRepaymentCalculationRequest(
    BigDecimal principal,
    LoanRepaymentType repaymentType,
    int installmentCount,
    int amountScale,
    int periodsPerYear,
    BigDecimal defaultAnnualRate,
    List<LoanRateSnapshotInput> rateSnapshots,
    List<LoanManualInstallmentInput> manualInstallments) {}
