package com.jiajunxie.domain.loan;

import java.math.BigDecimal;

public record LoanRecastPreviewResult(
    String installmentPlanId,
    int fromInstallmentNo,
    LoanRecastStrategy strategy,
    BigDecimal remainingPrincipal,
    int currentRemainingInstallments,
    int proposedRemainingInstallments,
    BigDecimal currentNextPaymentAmount,
    BigDecimal proposedNextPaymentAmount,
    boolean persistenceApplied) {}
