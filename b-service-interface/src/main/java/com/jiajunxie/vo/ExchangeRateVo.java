package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExchangeRateVo(
    String rateId,
    LocalDate rateDate,
    String baseCurrency,
    String quoteCurrency,
    BigDecimal exchangeRate,
    String rateSourceCode,
    Instant createdAt,
    boolean derived) {}
