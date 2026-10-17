package com.jiajunxie.domain.loan;

import java.math.BigDecimal;

public record LoanRecastExecutionCommand(
    String installmentPlanId,
    int fromInstallmentNo,
    BigDecimal remainingPrincipal,
    LoanRecastStrategy strategy,
    String reason) {}
