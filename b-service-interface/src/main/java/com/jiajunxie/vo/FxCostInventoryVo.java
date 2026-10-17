package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record FxCostInventoryVo(
    String accountId,
    String currency,
    BigDecimal accountBalance,
    BigDecimal fxQuantity,
    BigDecimal nonFxQuantity,
    boolean fxWithinAccountBalance,
    String inventoryMode,
    List<FxCostBucketVo> buckets,
    Instant updatedAt) {}
