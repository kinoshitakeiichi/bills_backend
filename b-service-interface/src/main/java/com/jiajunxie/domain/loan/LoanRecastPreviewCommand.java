package com.jiajunxie.domain.loan;

import java.math.BigDecimal;

public record LoanRecastPreviewCommand(
    String installmentPlanId,
    int fromInstallmentNo,
    BigDecimal remainingPrincipal,
    LoanRecastStrategy strategy) {}
