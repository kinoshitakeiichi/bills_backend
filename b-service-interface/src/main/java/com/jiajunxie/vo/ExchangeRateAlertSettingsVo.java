package com.jiajunxie.vo;

public record ExchangeRateAlertSettingsVo(
    Boolean masterEnabled,
    Integer pollingIntervalSeconds,
    Integer defaultCooldownMinutes,
    String defaultRateSourceCode,
    String defaultChannelCode,
    Boolean globalNotificationEnabled,
    String globalNotificationTitle,
    String globalNotificationBodyTemplate,
    String globalNotificationUrl,
    String barkBaseUrl,
    String barkSound,
    Integer barkCall,
    String barkGroupName,
    String barkLevel,
    Integer barkArchive,
    Double barkVolume,
    boolean barkDeviceKeyConfigured) {}
