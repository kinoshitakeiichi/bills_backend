package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record FxPositionVo(
    String positionId,
    String accountId,
    String currency,
    BigDecimal quantity,
    BigDecimal totalCost,
    String costCurrency,
    BigDecimal averageCostRate,
    Instant updatedAt) {}
