package com.jiajunxie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@TableName("bills.b_exchange_rate_alert_rules")
@Table(
    name = "b_exchange_rate_alert_rules",
    schema = "bills",
    indexes = {
      @Index(name = "idx_fx_alert_rules_enabled", columnList = "enabled"),
      @Index(
          name = "idx_fx_alert_rules_pair",
          columnList = "base_currency,quote_currency,rate_source_code")
    })
public class ExchangeRateAlertRule {

  @Id
  @TableId(value = "alert_rule_id", type = IdType.INPUT)
  @Column(name = "alert_rule_id", length = 36, nullable = false)
  private String alertRuleId;

  @Column(name = "rule_name", length = 100, nullable = false)
  private String ruleName;

  @Column(name = "base_currency", length = 10, nullable = false)
  private String baseCurrency;

  @Column(name = "quote_currency", length = 10, nullable = false)
  private String quoteCurrency;

  @Column(name = "rate_source_code", length = 50)
  private String rateSourceCode;

  @Column(name = "operator_code", length = 50, nullable = false)
  private String operatorCode;

  @Column(name = "threshold_mode_code", length = 30, nullable = false)
  private String thresholdModeCode = "FIXED";

  @Column(name = "threshold_rate", precision = 24, scale = 10)
  private BigDecimal thresholdRate;

  @Column(name = "channel_code", length = 50)
  private String channelCode;

  @Column(name = "cooldown_minutes")
  private Integer cooldownMinutes;

  @Column(name = "notification_title", length = 200)
  private String notificationTitle;

  @Column(name = "notification_body_template", length = 1000)
  private String notificationBodyTemplate;

  @Column(name = "notification_url", length = 1000)
  private String notificationUrl;

  @Column(name = "bark_level", length = 50)
  private String barkLevel;

  @Column(name = "bark_sound", length = 100)
  private String barkSound;

  @Column(name = "bark_call")
  private Integer barkCall;

  @Column(name = "bark_archive")
  private Integer barkArchive;

  @Column(name = "bark_volume")
  private Double barkVolume;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled = true;

  @Column(name = "last_observed_rate", precision = 24, scale = 10)
  private BigDecimal lastObservedRate;

  @Column(name = "last_triggered_at")
  private Instant lastTriggeredAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void createDefaults() {
    if (alertRuleId == null || alertRuleId.isBlank()) {
      alertRuleId = UUID.randomUUID().toString();
    }
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void updateTimestamp() {
    updatedAt = Instant.now();
  }
}
