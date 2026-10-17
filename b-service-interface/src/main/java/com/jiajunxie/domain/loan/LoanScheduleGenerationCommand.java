package com.jiajunxie.domain.loan;

import java.time.LocalDate;
import java.util.List;

public record LoanScheduleGenerationCommand(
    String loanAccountId,
    String sourceTransactionId,
    LoanRepaymentType repaymentType,
    int installmentCount,
    int amountScale,
    LocalDate firstScheduledPaymentDate,
    LoanRemainderRule remainderRule,
    LoanScheduleAdjustmentMode adjustmentMode,
    List<LoanManualInstallmentInput> manualInstallments) {}
