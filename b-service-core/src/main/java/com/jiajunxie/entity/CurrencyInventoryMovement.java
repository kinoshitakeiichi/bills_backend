package com.jiajunxie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

/**
 * Audit movement for FX Domain V3 currency inventory buckets.
 *
 * <p>The V3-3A historical cutover persists only current bucket state and deliberately creates no
 * synthetic transaction movements. V3-3B records only real post-cutover FX-cost changes here.
 */
@Data
@Entity
@TableName("bills.b_currency_inventory_movements")
@Table(
    name = "b_currency_inventory_movements",
    schema = "bills",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_currency_inventory_movement_order",
            columnNames = {"transaction_id", "movement_order"}),
    indexes = {
      @Index(
          name = "idx_currency_inventory_movement_account_time",
          columnList = "account_id,created_at"),
      @Index(
          name = "idx_currency_inventory_movement_bucket_time",
          columnList = "bucket_id,created_at")
    })
public class CurrencyInventoryMovement {
  @Id
  @TableId(value = "movement_id", type = IdType.INPUT)
  @Column(name = "movement_id", length = 36, nullable = false)
  private String movementId;

  @Column(name = "transaction_id", length = 36, nullable = false)
  private String transactionId;

  @Column(name = "bucket_id", length = 36, nullable = false)
  private String bucketId;

  @Column(name = "account_id", length = 36, nullable = false)
  private String accountId;

  @Column(name = "movement_order", nullable = false)
  private Integer movementOrder;

  @Column(name = "movement_type_code", length = 30, nullable = false)
  private String movementTypeCode;

  @Column(name = "origin_type_code", length = 30, nullable = false)
  private String originTypeCode;

  @Column(name = "currency", length = 10, nullable = false)
  private String currency;

  @Column(name = "source_currency", length = 10)
  private String sourceCurrency;

  @Column(name = "quantity_delta", precision = 19, scale = 4, nullable = false)
  private BigDecimal quantityDelta;

  @Column(name = "source_cost_delta", precision = 19, scale = 4)
  private BigDecimal sourceCostDelta;

  @Column(name = "allocation_ratio", precision = 18, scale = 12)
  private BigDecimal allocationRatio;

  @Column(name = "valuation_rate", precision = 24, scale = 10)
  private BigDecimal valuationRate;

  @Column(name = "equivalent_cost_account_currency", precision = 19, scale = 4)
  private BigDecimal equivalentCostAccountCurrency;

  @Column(name = "realized_gain_loss_account_currency", precision = 19, scale = 4)
  private BigDecimal realizedGainLossAccountCurrency;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
