package com.jiajunxie.domain.budget;

import java.math.BigDecimal;
import java.time.Instant;

public record BudgetActualFacts(
    String transactionId,
    String transactionType,
    String originCode,
    String originReferenceId,
    Instant transactionTime,
    BigDecimal eligibleAmount,
    String currency,
    BudgetTargetMatcherContract.Facts targetFacts) {}
