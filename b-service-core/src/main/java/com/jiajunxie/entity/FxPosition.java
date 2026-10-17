package com.jiajunxie.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@TableName("bills.b_fx_positions")
@Table(
    name = "b_fx_positions",
    schema = "bills",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_fx_position_account_currency",
            columnNames = {"account_id", "currency"}),
    indexes = @Index(name = "idx_fx_positions_account", columnList = "account_id"),
    check =
        @CheckConstraint(
            name = "ck_quantity_cost",
            constraint = "quantity >= 0 AND total_cost >= 0"))
public class FxPosition {
  @Id
  @TableId(value = "position_id", type = IdType.INPUT)
  @Column(name = "position_id", length = 36, nullable = false)
  private String positionId;

  @Column(name = "account_id", length = 36, nullable = false)
  private String accountId;

  @Column(name = "currency", length = 10, nullable = false)
  private String currency;

  @Column(name = "quantity", precision = 19, scale = 4, nullable = false)
  private BigDecimal quantity = BigDecimal.ZERO;

  @Column(name = "total_cost", precision = 19, scale = 4, nullable = false)
  private BigDecimal totalCost = BigDecimal.ZERO;

  @Column(name = "cost_currency", length = 10, nullable = false)
  private String costCurrency;

  @Column(name = "average_cost_rate", precision = 24, scale = 10, nullable = false)
  private BigDecimal averageCostRate = BigDecimal.ZERO;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void p() {
    if (positionId == null || positionId.isBlank()) positionId = UUID.randomUUID().toString();
    updatedAt = Instant.now();
  }

  @PreUpdate
  void u() {
    updatedAt = Instant.now();
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
      foreignKey = @ForeignKey(name = "fk_fx_positions_account"))
  private Account account;
}
