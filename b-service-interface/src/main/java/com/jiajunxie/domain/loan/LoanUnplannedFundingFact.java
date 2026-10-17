package com.jiajunxie.domain.loan;

import java.math.BigDecimal;
import java.time.Instant;

public record LoanUnplannedFundingFact(
    String transactionId,
    String transactionTypeCode,
    Instant transactionTime,
    BigDecimal principalAmount,
    String currency,
    String merchant,
    String remark) {}
