package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record FxLegacyRebuildStepVo(
    Instant transactionTime,
    String transactionId,
    String transactionType,
    String movementType,
    BigDecimal quantityDelta,
    BigDecimal costDelta,
    BigDecimal valuationRate,
    String rateSourceCode,
    BigDecimal realizedGainLoss,
    BigDecimal quantityAfter,
    BigDecimal totalCostAfter,
    BigDecimal averageCostAfter) {}
