package com.jiajunxie.domain.loan;

import java.math.BigDecimal;
import java.util.List;

public record LoanSchedulePlanView(
    String installmentPlanId,
    String sourceTransactionId,
    String loanAccountId,
    String currency,
    BigDecimal originalPrincipal,
    BigDecimal effectiveTotalPrincipal,
    BigDecimal effectiveTotalInterest,
    BigDecimal effectiveTotalFee,
    List<LoanScheduleItem> schedules) {}
