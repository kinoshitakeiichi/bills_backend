package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record FxExchangeCreateVo(
    String fxDirectionCode,
    String sourceAccountId,
    String targetAccountId,
    BigDecimal sourceAmount,
    String sourceCurrency,
    BigDecimal targetAmount,
    String targetCurrency,
    BigDecimal executedRate,
    BigDecimal feeAmount,
    String feeCurrency,
    String remark,
    Instant transactionTime) {}
