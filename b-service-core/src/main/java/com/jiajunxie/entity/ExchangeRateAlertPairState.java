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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@TableName("bills.b_exchange_rate_alert_pair_states")
@Table(
    name = "b_exchange_rate_alert_pair_states",
    schema = "bills",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_fx_alert_pair_state",
            columnNames = {"base_currency", "quote_currency", "rate_source_code"}),
    indexes =
        @Index(
            name = "idx_fx_alert_pair_state_pair",
            columnList = "base_currency,quote_currency,rate_source_code"))
public class ExchangeRateAlertPairState {

  @Id
  @TableId(value = "pair_state_id", type = IdType.INPUT)
  @Column(name = "pair_state_id", length = 36, nullable = false)
  private String pairStateId;

  @Column(name = "base_currency", length = 10, nullable = false)
  private String baseCurrency;

  @Column(name = "quote_currency", length = 10, nullable = false)
  private String quoteCurrency;

  @Column(name = "rate_source_code", length = 50, nullable = false)
  private String rateSourceCode;

  @Column(name = "last_observed_rate", precision = 24, scale = 10)
  private java.math.BigDecimal lastObservedRate;

  @Column(name = "last_global_triggered_at")
  private Instant lastGlobalTriggeredAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void createDefaults() {
    if (pairStateId == null || pairStateId.isBlank()) pairStateId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void updateTimestamp() {
    updatedAt = Instant.now();
  }
}
