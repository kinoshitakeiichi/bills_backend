package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record StatementPaymentAllocationVo(
    String paymentAllocationId,
    String paymentTransactionId,
    String statementId,
    BigDecimal allocatedAmount,
    String currency,
    Integer allocationOrder,
    Instant createdAt) {}
