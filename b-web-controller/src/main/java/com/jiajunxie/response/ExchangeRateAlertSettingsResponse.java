package com.jiajunxie.response;

import lombok.Data;

@Data
public class ExchangeRateAlertSettingsResponse {
  private Boolean masterEnabled;
  private Integer pollingIntervalSeconds;
  private Integer defaultCooldownMinutes;
  private String defaultRateSourceCode;
  private String defaultChannelCode;
  private Boolean globalNotificationEnabled;
  private String globalNotificationTitle;
  private String globalNotificationBodyTemplate;
  private String globalNotificationUrl;
  private String barkBaseUrl;
  private String barkSound;
  private Integer barkCall;
  private String barkGroupName;
  private String barkLevel;
  private Integer barkArchive;
  private Double barkVolume;
  private boolean barkDeviceKeyConfigured;
}
