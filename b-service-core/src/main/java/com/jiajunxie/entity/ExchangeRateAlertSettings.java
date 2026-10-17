package com.jiajunxie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Data;

@Data
@Entity
@TableName("bills.b_exchange_rate_alert_settings")
@Table(name = "b_exchange_rate_alert_settings", schema = "bills")
public class ExchangeRateAlertSettings {

  public static final String DEFAULT_SETTINGS_ID = "DEFAULT";

  @Id
  @TableId(value = "settings_id", type = IdType.INPUT)
  @Column(name = "settings_id", length = 36, nullable = false)
  private String settingsId = DEFAULT_SETTINGS_ID;

  @Column(name = "master_enabled", nullable = false)
  private Boolean masterEnabled = true;

  @Column(name = "polling_interval_seconds", nullable = false)
  private Integer pollingIntervalSeconds = 60;

  @Column(name = "default_cooldown_minutes", nullable = false)
  private Integer defaultCooldownMinutes = 60;

  @Column(name = "default_rate_source_code", length = 50, nullable = false)
  private String defaultRateSourceCode = "TRADINGVIEW";

  @Column(name = "default_channel_code", length = 50, nullable = false)
  private String defaultChannelCode = "BARK";

  @Column(name = "global_notification_enabled", nullable = false)
  private Boolean globalNotificationEnabled = true;

  @Column(name = "global_notification_title", length = 200)
  private String globalNotificationTitle = "{baseCurrency}/{quoteCurrency} 汇率通知";

  @Column(name = "global_notification_body_template", length = 1000)
  private String globalNotificationBodyTemplate =
      "{baseCurrency}/{quoteCurrency} 当前汇率：{currentRate}\n来源：{rateSource}";

  @Column(name = "global_notification_url", length = 1000)
  private String globalNotificationUrl;

  @Column(name = "bark_base_url", length = 500, nullable = false)
  private String barkBaseUrl = "https://api.day.app/push";

  @Column(name = "bark_sound", length = 100)
  private String barkSound;

  @Column(name = "bark_call")
  private Integer barkCall;

  @Column(name = "bark_group_name", length = 100)
  private String barkGroupName;

  @Column(name = "bark_level", length = 50)
  private String barkLevel;

  @Column(name = "bark_archive")
  private Integer barkArchive;

  @Column(name = "bark_volume")
  private Double barkVolume;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void createTimestamps() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void updateTimestamp() {
    updatedAt = Instant.now();
  }
}
