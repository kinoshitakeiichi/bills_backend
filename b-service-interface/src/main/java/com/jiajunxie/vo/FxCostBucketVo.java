package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record FxCostBucketVo(
    String bucketId,
    String sourceCurrency,
    BigDecimal quantity,
    BigDecimal sourceCostAmount,
    BigDecimal averageCostRate,
    Instant updatedAt) {}
