package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FxPositionValuationVo(
    String positionId,
    String accountId,
    String currency,
    BigDecimal quantity,
    BigDecimal totalCost,
    String costCurrency,
    BigDecimal averageCostRate,
    String valuationMode,
    LocalDate valuationDate,
    BigDecimal effectiveRate,
    String rateSourceCode,
    LocalDate rateDate,
    Instant observedAt,
    boolean derivedRate,
    BigDecimal marketValue,
    BigDecimal unrealizedGainLoss,
    BigDecimal unrealizedReturnRate) {}
