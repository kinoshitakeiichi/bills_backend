package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record StatementAdjustmentVo(
    String statementAdjustmentId,
    String statementId,
    String adjustmentTypeCode,
    BigDecimal amount,
    String currency,
    LocalDate oldPaymentDate,
    LocalDate newPaymentDate,
    String reason,
    String statusCode,
    String linkedTransactionId,
    String reversalTransactionId,
    Instant reversedAt,
    Instant createdAt,
    Instant updatedAt) {}
