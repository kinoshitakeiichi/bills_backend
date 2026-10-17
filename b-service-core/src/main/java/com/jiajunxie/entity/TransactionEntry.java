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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@TableName("bills.b_transaction_entries")
@Table(
    name = "b_transaction_entries",
    schema = "bills",
    indexes = {
      @Index(name = "idx_entries_account_transaction", columnList = "account_id,transaction_id"),
      @Index(name = "idx_entries_transaction", columnList = "transaction_id"),
      @Index(name = "idx_entries_account", columnList = "account_id")
    },
    check = @CheckConstraint(name = "ck_amount", constraint = "amount <> 0"))
public class TransactionEntry {
  @Id
  @TableId(value = "entry_id", type = IdType.INPUT)
  @Column(name = "entry_id", length = 36, nullable = false)
  private String entryId;

  @Column(name = "transaction_id", length = 36, nullable = false)
  private String transactionId;

  @Column(name = "account_id", length = 36, nullable = false)
  private String accountId;

  @Column(name = "amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal amount;

  @Column(name = "currency", length = 10, nullable = false)
  private String currency;

  /** Compatibility property; persisted with the V2 column name. */
  @TableField("entry_role_code")
  @Column(name = "entry_role_code", length = 30, nullable = false)
  private String entryRole;

  @Column(name = "created_at", nullable = false, updatable = false)
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
      foreignKey = @ForeignKey(name = "fk_entries_transaction"))
  private Transaction transaction;

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
      foreignKey = @ForeignKey(name = "fk_entries_account"))
  private Account account;

  @PrePersist
  void prePersist() {
    if (entryId == null || entryId.isBlank()) entryId = UUID.randomUUID().toString();
    if (createdAt == null) createdAt = Instant.now();
  }
}
