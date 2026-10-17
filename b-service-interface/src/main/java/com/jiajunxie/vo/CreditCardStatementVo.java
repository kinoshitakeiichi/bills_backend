package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CreditCardStatementVo(
    String statementId,
    String creditAccountId,
    String statementNumber,
    LocalDate periodStart,
    LocalDate periodEnd,
    LocalDate confirmationDate,
    LocalDate scheduledPaymentDate,
    LocalDate calculatedPaymentDate,
    LocalDate manualPaymentDate,
    BigDecimal principalAmount,
    BigDecimal feeAmount,
    BigDecimal adjustmentAmount,
    BigDecimal totalAmount,
    BigDecimal postAdjustmentAmount,
    BigDecimal adjustedTotalAmount,
    BigDecimal paidAmount,
    BigDecimal outstandingAmount,
    LocalDate effectivePaymentDate,
    String currency,
    String statusCode,
    Instant confirmedAt,
    Instant reopenedAt,
    String reopenReason,
    Integer reopenCount,
    List<CreditCardStatementItemVo> items,
    List<StatementPaymentAllocationVo> payments,
    List<StatementAdjustmentVo> adjustments) {}
