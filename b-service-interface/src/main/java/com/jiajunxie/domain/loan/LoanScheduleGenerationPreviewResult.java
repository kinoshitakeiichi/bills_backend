package com.jiajunxie.domain.loan;

import java.math.BigDecimal;
import java.util.List;

public record LoanScheduleGenerationPreviewResult(
    String sourceTransactionId,
    String loanAccountId,
    String currency,
    BigDecimal originalPrincipal,
    BigDecimal totalPrincipal,
    BigDecimal totalInterest,
    BigDecimal totalFee,
    BigDecimal totalPayment,
    String calculationVersion,
    List<LoanScheduleGenerationPreviewItem> installments) {}
