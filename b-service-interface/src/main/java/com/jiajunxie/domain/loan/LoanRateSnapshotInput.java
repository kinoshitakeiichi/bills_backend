package com.jiajunxie.domain.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanRateSnapshotInput(
    int effectiveInstallmentNo,
    BigDecimal annualRate,
    LocalDate effectiveFrom) {}
