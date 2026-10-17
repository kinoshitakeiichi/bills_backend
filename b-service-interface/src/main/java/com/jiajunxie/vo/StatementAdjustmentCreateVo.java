package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StatementAdjustmentCreateVo(
    String adjustmentTypeCode,
    BigDecimal amount,
    String currency,
    LocalDate oldPaymentDate,
    LocalDate newPaymentDate,
    String reason) {}
