package com.jiajunxie.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExchangeRateAlertSettingsRequest {

  @NotNull private Boolean masterEnabled;

  @NotNull
  @Min(10)
  @Max(86400)
  private Integer pollingIntervalSeconds;

  @NotNull
  @Min(0)
  @Max(10080)
  private Integer defaultCooldownMinutes;

  @NotBlank @Size(max = 50) private String defaultRateSourceCode;
  @NotBlank @Size(max = 50) private String defaultChannelCode;

  @NotNull private Boolean globalNotificationEnabled;
  @Size(max = 200) private String globalNotificationTitle;
  @Size(max = 1000) private String globalNotificationBodyTemplate;
  @Size(max = 1000) private String globalNotificationUrl;

  @NotBlank @Size(max = 500) private String barkBaseUrl;
  @Size(max = 100) private String barkSound;
  @Min(0) @Max(1) private Integer barkCall;
  @Size(max = 100) private String barkGroupName;
  @Size(max = 50) private String barkLevel;
  @Min(0) @Max(1) private Integer barkArchive;
  @Min(0) @Max(10) private Double barkVolume;
}
