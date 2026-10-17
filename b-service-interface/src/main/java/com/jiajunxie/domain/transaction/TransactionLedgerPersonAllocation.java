package com.jiajunxie.domain.transaction;

import java.math.BigDecimal;

public record TransactionLedgerPersonAllocation(
    String personId,
    String personName,
    BigDecimal allocatedAmount,
    String currency,
    BigDecimal allocationRatio) {}
