package com.jiajunxie.response;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

@Data
public class ExchangeRateAlertRuleResponse {
  private String alertRuleId;
  private String ruleName;
  private String baseCurrency;
  private String quoteCurrency;
  private String rateSourceCode;
  private String operatorCode;
  private String thresholdModeCode;
  private BigDecimal thresholdRate;
  private String channelCode;
  private Integer cooldownMinutes;
  private String notificationTitle;
  private String notificationBodyTemplate;
  private String notificationUrl;
  private String barkLevel;
  private String barkSound;
  private Integer barkCall;
  private Integer barkArchive;
  private Double barkVolume;
  private Boolean enabled;
  private BigDecimal lastObservedRate;
  private Instant lastTriggeredAt;
}
