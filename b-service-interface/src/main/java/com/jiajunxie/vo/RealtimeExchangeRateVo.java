package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record RealtimeExchangeRateVo(
    String baseCurrency,
    String quoteCurrency,
    BigDecimal exchangeRate,
    String rateSourceCode,
    Instant observedAt,
    boolean derived) {}
