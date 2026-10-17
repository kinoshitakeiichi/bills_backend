package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FxCostInventoryValuationVo(
    String accountId,
    String currency,
    BigDecimal fxQuantity,
    BigDecimal nonFxQuantity,
    String valuationMode,
    LocalDate valuationDate,
    boolean valuationComplete,
    BigDecimal equivalentCostAccountCurrency,
    BigDecimal marketValueAccountCurrency,
    BigDecimal unrealizedGainLossAccountCurrency,
    BigDecimal unrealizedReturnRate,
    List<FxCostBucketValuationVo> buckets) {}
