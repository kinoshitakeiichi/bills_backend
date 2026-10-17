package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FxCostBucketValuationVo(
    String bucketId,
    String sourceCurrency,
    BigDecimal quantity,
    BigDecimal sourceCostAmount,
    BigDecimal averageCostRate,
    boolean rateAvailable,
    BigDecimal effectiveRate,
    String rateSourceCode,
    LocalDate rateDate,
    Instant observedAt,
    boolean derivedRate,
    BigDecimal equivalentCostAccountCurrency,
    BigDecimal unrealizedGainLossAccountCurrency,
    BigDecimal unrealizedReturnRate,
    String rateError) {}
