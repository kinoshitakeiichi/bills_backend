package com.jiajunxie.domain.loan;

import java.time.LocalDate;

public record LoanScheduleDateAdjustmentCommand(
    String scheduleId,
    LocalDate manualPaymentDate,
    String reason) {}
