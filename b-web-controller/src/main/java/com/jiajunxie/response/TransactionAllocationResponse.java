package com.jiajunxie.response;

import java.math.BigDecimal;

public record TransactionAllocationResponse(
    String personId,
    BigDecimal allocatedAmount,
    String currency,
    BigDecimal allocationRatio) {}
