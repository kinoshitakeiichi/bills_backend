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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 交易的人员归属与分摊记录。
 *
 * <p>付款账户归属人与消费归属人可能不同；该实体用于按人员统计收入、支出和预算。
 */
@Data
@Entity
@Table(
    name = "b_transaction_allocations",
    schema = "bills",
    indexes = {
      @Index(name = "idx_allocations_transaction", columnList = "transaction_id"),
      @Index(name = "idx_allocations_person", columnList = "person_id"),
      @Index(name = "idx_allocations_person_transaction", columnList = "person_id, transaction_id")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_allocation_transaction_person",
          columnNames = {"transaction_id", "person_id"})
    },
    check =
        @CheckConstraint(
            name = "ck_allocation_amount_ratio",
            constraint =
                "allocated_amount >= 0 AND (allocation_ratio IS NULL OR (allocation_ratio >= 0 AND"
                    + " allocation_ratio <= 1))"))
@TableName("bills.b_transaction_allocations")
public class TransactionAllocation {

  @Id
  @TableId(value = "allocation_id", type = IdType.INPUT)
  @Column(name = "allocation_id", length = 36, nullable = false, comment = "分摊ID")
  private String allocationId;

  @Column(name = "transaction_id", length = 36, nullable = false, comment = "交易ID")
  private String transactionId;

  @Column(name = "person_id", length = 36, nullable = false, comment = "消费或收入归属人员")
  private String personId;

  @Column(
      name = "allocated_amount",
      precision = 19,
      scale = 4,
      nullable = false,
      comment = "人员承担金额，使用正数")
  private BigDecimal allocatedAmount;

  @Column(name = "currency", length = 10, nullable = false, comment = "分摊币种")
  private String currency;

  @Column(name = "allocation_ratio", precision = 9, scale = 6, comment = "可选分摊比例")
  private BigDecimal allocationRatio;

  @Column(name = "created_at", nullable = false, updatable = false, comment = "创建时间")
  private Instant createdAt;

  // Read-only JPA associations: generate database foreign keys while business code continues using
  // scalar IDs.
  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(
      name = "transaction_id",
      referencedColumnName = "transaction_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_allocations_transaction"))
  private Transaction transaction;

  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "person_id",
      referencedColumnName = "person_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_allocations_person"))
  private Person person;

  @PrePersist
  public void prePersist() {
    if (allocationId == null || allocationId.isBlank()) {
      allocationId = UUID.randomUUID().toString();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
