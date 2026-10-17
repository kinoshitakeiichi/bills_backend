package com.jiajunxie.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@TableName("bills.b_loan_rate_histories")
@Table(
    name = "b_loan_rate_histories",
    schema = "bills",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_loan_rate_effective",
            columnNames = {"account_id", "effective_date"}),
    indexes =
        @Index(name = "idx_loan_rates_account_date", columnList = "account_id,effective_date"))
public class LoanRateHistory {
  @Id
  @TableId(value = "rate_history_id", type = IdType.INPUT)
  @Column(name = "rate_history_id", length = 36, nullable = false)
  private String rateHistoryId;

  @Column(name = "account_id", length = 36, nullable = false)
  private String accountId;

  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;

  @Column(name = "annual_rate", precision = 12, scale = 8, nullable = false)
  private BigDecimal annualRate;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void p() {
    if (rateHistoryId == null || rateHistoryId.isBlank())
      rateHistoryId = UUID.randomUUID().toString();
    if (createdAt == null) createdAt = Instant.now();
  }

  // Read-only JPA associations: generate database foreign keys while business code continues using
  // scalar IDs.
  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "account_id",
      referencedColumnName = "account_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_loan_rates_profile"))
  private LoanProfile loanProfile;
}
