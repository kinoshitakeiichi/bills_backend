package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record CreditCardStatementItemVo(
    String statementItemId,
    String scheduleId,
    String sourceTransactionId,
    Instant transactionTime,
    Integer installmentNo,
    Integer installmentCount,
    BigDecimal principalAmount,
    BigDecimal feeAmount,
    BigDecimal adjustmentAmount,
    BigDecimal billedAmount,
    String currency,
    String itemStatusCode) {}
