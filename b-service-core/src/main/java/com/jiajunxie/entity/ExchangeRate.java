package com.jiajunxie.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Data
@Entity
@TableName("bills.b_exchange_rates")
@Table(
    name = "b_exchange_rates",
    schema = "bills",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_exchange_rate",
            columnNames = {"rate_date", "base_currency", "quote_currency", "rate_source_code"}),
    indexes =
        @Index(
            name = "idx_exchange_rates_pair_date",
            columnList = "base_currency,quote_currency,rate_date"),
    check = @CheckConstraint(name = "ck_rate", constraint = "exchange_rate > 0"))
public class ExchangeRate {
  @Id
  @TableId(value = "rate_id", type = IdType.INPUT)
  @Column(name = "rate_id", length = 36, nullable = false)
  private String rateId;

  @Column(name = "rate_date", nullable = false)
  private LocalDate rateDate;

  @Column(name = "base_currency", length = 10, nullable = false)
  private String baseCurrency;

  @Column(name = "quote_currency", length = 10, nullable = false)
  private String quoteCurrency;

  @Column(name = "exchange_rate", precision = 24, scale = 10, nullable = false)
  private BigDecimal exchangeRate;

  @Column(name = "rate_source_code", length = 50, nullable = false)
  private String rateSourceCode;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void p() {
    if (rateId == null || rateId.isBlank()) rateId = UUID.randomUUID().toString();
    if (createdAt == null) createdAt = Instant.now();
  }
}
