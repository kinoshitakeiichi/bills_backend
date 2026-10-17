package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record ExchangeRateAlertRuleVo(
    String alertRuleId,
    String ruleName,
    String baseCurrency,
    String quoteCurrency,
    String rateSourceCode,
    String operatorCode,
    String thresholdModeCode,
    BigDecimal thresholdRate,
    String channelCode,
    Integer cooldownMinutes,
    String notificationTitle,
    String notificationBodyTemplate,
    String notificationUrl,
    String barkLevel,
    String barkSound,
    Integer barkCall,
    Integer barkArchive,
    Double barkVolume,
    Boolean enabled,
    BigDecimal lastObservedRate,
    Instant lastTriggeredAt) {}
