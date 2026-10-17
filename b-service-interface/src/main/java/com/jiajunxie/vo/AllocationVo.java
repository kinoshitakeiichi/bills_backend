package com.jiajunxie.vo;

import java.math.BigDecimal;

public record AllocationVo(
    String personId,
    BigDecimal allocatedAmount,
    String currency,
    BigDecimal allocationRatio) {}
