package com.jiajunxie.domain.loan;

import java.math.BigDecimal;
import java.util.List;

public record LoanRepaymentCalculationResult(
    BigDecimal originalPrincipal,
    BigDecimal totalPrincipal,
    BigDecimal totalInterest,
    BigDecimal totalFee,
    BigDecimal totalPayment,
    String calculationVersion,
    List<LoanRepaymentInstallment> installments) {}
