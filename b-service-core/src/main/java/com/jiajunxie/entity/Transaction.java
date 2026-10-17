package com.jiajunxie.entity;

import java.time.Instant;
import java.util.UUID;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@TableName("bills.b_transactions")
@Table(
    name = "b_transactions",
    schema = "bills",
    indexes = {
      @Index(name = "idx_transactions_time", columnList = "transaction_time"),
      @Index(
          name = "idx_transactions_type_time",
          columnList = "transaction_type_code,transaction_time"),
      @Index(name = "idx_transactions_category", columnList = "category_id")
    })
public class Transaction {
  @Id
  @TableId(value = "transaction_id", type = IdType.INPUT)
  @Column(name = "transaction_id", length = 36, nullable = false)
  private String transactionId;

  /** Compatibility property; persisted with the V2 column name. */
  @TableField("transaction_type_code")
  @Column(name = "transaction_type_code", length = 30, nullable = false)
  private String transactionType;

  // These three user-editable fields are nullable by design. MyBatis-Plus updateById uses a
  // NOT_NULL update strategy by default, which would silently keep the old database value when an
  // edit intentionally sends null (for example, clearing merchant or remark). ALWAYS is scoped to
  // these nullable fields so "clear this value" is persisted without changing update semantics for
  // origin metadata or other fields.
  @TableField(value = "category_id", updateStrategy = FieldStrategy.ALWAYS)
  @Column(name = "category_id", length = 36)
  private String categoryId;

  @TableField(value = "merchant", updateStrategy = FieldStrategy.ALWAYS)
  @Column(name = "merchant", length = 150)
  private String merchant;

  @TableField(value = "remark", updateStrategy = FieldStrategy.ALWAYS)
  @Column(name = "remark", columnDefinition = "text")
  private String remark;

  @Column(name = "transaction_time", nullable = false)
  private Instant transactionTime;

  @Column(name = "origin_code", length = 30)
  private String originCode;

  @Column(name = "origin_reference_id", length = 36)
  private String originReferenceId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // Read-only JPA associations: generate database foreign keys while business code continues using
  // scalar IDs.
  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(
      name = "category_id",
      referencedColumnName = "category_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_transactions_category"))
  private Category category;

  @PrePersist
  void prePersist() {
    var now = Instant.now();
    if (transactionId == null || transactionId.isBlank())
      transactionId = UUID.randomUUID().toString();
    if (transactionTime == null) transactionTime = now;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
