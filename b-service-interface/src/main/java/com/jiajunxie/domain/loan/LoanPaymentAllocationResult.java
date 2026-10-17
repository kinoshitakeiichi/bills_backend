package com.jiajunxie.domain.loan;

import java.math.BigDecimal;

public record LoanPaymentAllocationResult(
    String loanPaymentTransactionId,
    BigDecimal allocatedPrincipalAmount,
    BigDecimal allocatedInterestAmount,
    BigDecimal allocatedFeeAmount,
    BigDecimal unallocatedPrincipalAmount,
    BigDecimal unallocatedInterestAmount,
    BigDecimal unallocatedFeeAmount,
    int touchedScheduleCount) {}
