package com.jiajunxie.domain.loan;

import java.math.BigDecimal;

public record LoanScheduleAmountAdjustmentCommand(
    String scheduleId,
    LoanScheduleAmountField field,
    BigDecimal newAmount,
    String reason) {}
