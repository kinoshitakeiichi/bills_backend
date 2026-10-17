package com.jiajunxie.vo;

import java.math.BigDecimal;

public record CurrencyInventoryBucketPreviewVo(
    String originTypeCode,
    String sourceCurrency,
    BigDecimal quantity,
    BigDecimal sourceCostAmount,
    BigDecimal averageCostRate) {}
