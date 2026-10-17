package com.jiajunxie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

/**
 * FX Domain V3 derived inventory bucket.
 *
 * <p>V3-3A persists the reviewed historical cutover snapshot. V3-3B then maintains this state for
 * post-cutover business flows. Account/TransactionEntry stay the balance source of truth;
 * FX_EXCHANGE buckets retain original source-currency cost.
 */
@Data
@Entity
@TableName("bills.b_currency_inventory_buckets")
@Table(
    name = "b_currency_inventory_buckets",
    schema = "bills",
    indexes = @Index(name = "idx_currency_inventory_account", columnList = "account_id"),
    check = @CheckConstraint(name = "ck_currency_inventory_bucket_quantity", constraint = "quantity >= 0"))
public class CurrencyInventoryBucket {
  @Id
  @TableId(value = "bucket_id", type = IdType.INPUT)
  @Column(name = "bucket_id", length = 36, nullable = false)
  private String bucketId;

  @Column(name = "account_id", length = 36, nullable = false)
  private String accountId;

  @Column(name = "currency", length = 10, nullable = false)
  private String currency;

  @Column(name = "origin_type_code", length = 30, nullable = false)
  private String originTypeCode;

  @Column(name = "source_currency", length = 10)
  private String sourceCurrency;

  @Column(name = "quantity", precision = 19, scale = 4, nullable = false)
  private BigDecimal quantity;

  @Column(name = "source_cost_amount", precision = 19, scale = 4)
  private BigDecimal sourceCostAmount;

  @Column(name = "average_cost_rate", precision = 24, scale = 10)
  private BigDecimal averageCostRate;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
