package com.jiajunxie.vo;

import java.math.BigDecimal;

public record FxExchangeCreatedVo(
    String transactionId,
    String fxDirectionCode,
    BigDecimal costBasisAmount,
    BigDecimal realizedGainLoss,
    String gainLossCurrency) {}
