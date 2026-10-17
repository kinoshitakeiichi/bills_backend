package com.jiajunxie.entity;

import java.math.BigDecimal;
import java.time.Instant;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@TableName("bills.b_fx_details")
@Table(name = "b_fx_details", schema = "bills")
public class FxDetail {
  @Id
  @TableId(value = "transaction_id", type = IdType.INPUT)
  @Column(name = "transaction_id", length = 36, nullable = false)
  private String transactionId;

  @Column(name = "fx_direction_code", length = 30, nullable = false)
  private String fxDirectionCode;

  @Column(name = "source_account_id", length = 36, nullable = false)
  private String sourceAccountId;

  @Column(name = "target_account_id", length = 36, nullable = false)
  private String targetAccountId;

  @Column(name = "source_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal sourceAmount;

  @Column(name = "source_currency", length = 10, nullable = false)
  private String sourceCurrency;

  @Column(name = "target_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal targetAmount;

  @Column(name = "target_currency", length = 10, nullable = false)
  private String targetCurrency;

  @Column(name = "executed_rate", precision = 24, scale = 10, nullable = false)
  private BigDecimal executedRate;

  @Column(name = "fee_amount", precision = 19, scale = 4)
  private BigDecimal feeAmount;

  @Column(name = "fee_currency", length = 10)
  private String feeCurrency;

  @Column(name = "cost_basis_amount", precision = 19, scale = 4)
  private BigDecimal costBasisAmount;

  @Column(name = "realized_gain_loss", precision = 19, scale = 4)
  private BigDecimal realizedGainLoss;

  @Column(name = "gain_loss_currency", length = 10)
  private String gainLossCurrency;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  // Read-only JPA associations: generate database foreign keys while business code continues using
  // scalar IDs.
  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @OneToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(
      name = "transaction_id",
      referencedColumnName = "transaction_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_fx_details_transaction"))
  private Transaction transaction;
}
