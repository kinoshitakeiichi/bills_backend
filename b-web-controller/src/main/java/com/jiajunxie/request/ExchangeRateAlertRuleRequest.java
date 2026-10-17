package com.jiajunxie.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class ExchangeRateAlertRuleRequest {

  @NotBlank @Size(max = 100) private String ruleName;
  @NotBlank @Size(max = 10) private String baseCurrency;
  @NotBlank @Size(max = 10) private String quoteCurrency;
  @Size(max = 50) private String rateSourceCode;

  @NotBlank @Size(max = 50) private String operatorCode;

  @NotBlank @Size(max = 30) private String thresholdModeCode;

  @DecimalMin(value = "0", inclusive = false)
  private BigDecimal thresholdRate;

  @Size(max = 50) private String channelCode;

  @Min(0)
  @Max(10080)
  private Integer cooldownMinutes;

  @Size(max = 200) private String notificationTitle;
  @Size(max = 1000) private String notificationBodyTemplate;
  @Size(max = 1000) private String notificationUrl;

  @Size(max = 50) private String barkLevel;
  @Size(max = 100) private String barkSound;
  @Min(0) @Max(1) private Integer barkCall;
  @Min(0) @Max(1) private Integer barkArchive;
  @Min(0) @Max(10) private Double barkVolume;

  private Boolean enabled;
}
