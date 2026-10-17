package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StatementAdjustmentSummaryVo(
    BigDecimal postAdjustmentAmount,
    BigDecimal adjustedTotalAmount,
    BigDecimal outstandingAmount,
    LocalDate effectivePaymentDate,
    List<StatementAdjustmentVo> adjustments) {}
