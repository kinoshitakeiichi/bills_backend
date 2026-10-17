package com.jiajunxie.entity;

import java.math.BigDecimal;
import java.time.Instant;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@TableName("bills.b_accounts")
@Table(
    name = "b_accounts",
    schema = "bills",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_accounts_code_currency",
            columnNames = {"account_code", "currency"}),
    indexes = {
      @Index(name = "idx_accounts_owner", columnList = "owner_person_id"),
      @Index(name = "idx_accounts_parent", columnList = "parent_account_id"),
      @Index(name = "idx_accounts_class_type", columnList = "account_class_code,account_type_code"),
      @Index(name = "idx_accounts_enabled", columnList = "enabled")
    })
public class Account {
  @Id
  @TableId(value = "account_id", type = IdType.INPUT)
  @Column(name = "account_id", length = 36, nullable = false)
  private String accountId;

  @Column(name = "account_name", length = 100, nullable = false)
  private String accountName;

  @Column(name = "account_code", length = 100, nullable = false)
  private String accountCode;

  @Column(name = "issuer", length = 100)
  private String issuer;

  /** Compatibility property; persisted with the V2 column name. */
  @TableField("account_type_code")
  @Column(name = "account_type_code", length = 30, nullable = false)
  private String accountTypeCode;

  @Column(name = "account_class_code", length = 30, nullable = false)
  private String accountClassCode;

  @Column(
      name = "total_amount",
      precision = 19,
      scale = 4,
      nullable = false,
      columnDefinition = "NUMERIC(19,4) DEFAULT 0")
  private BigDecimal totalAmount = BigDecimal.ZERO;

  @Column(name = "currency", length = 10, nullable = false)
  private String currency;

  /** Compatibility property for existing VO/MapStruct; value is a Person ID. */
  @TableField("owner_person_id")
  @Column(name = "owner_person_id", length = 36, nullable = false)
  private String ownerPersonId;

  /** Compatibility property; persisted with the V2 column name. */
  @TableField("parent_account_id")
  @Column(name = "parent_account_id", length = 36)
  private String parentAccountId;

  /** Compatibility property; persisted with the V2 column name. */
  @TableField("auto_debit_account_id")
  @Column(name = "auto_debit_account_id", length = 36)
  private String autoDebitAccountId;

  @Column(name = "is_base_account", nullable = false)
  private Boolean isBaseAccount = false;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled = true;

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
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "owner_person_id",
      referencedColumnName = "person_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_accounts_owner_person"))
  private Person ownerPerson;

  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(
      name = "parent_account_id",
      referencedColumnName = "account_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_accounts_parent"))
  private Account parentAccountEntity;

  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(
      name = "auto_debit_account_id",
      referencedColumnName = "account_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_accounts_auto_debit"))
  private Account autoDebitAccountEntity;

  @PrePersist
  void prePersist() {
    var now = Instant.now();
    if (accountId == null || accountId.isBlank()) accountId = UUID.randomUUID().toString();
    if (totalAmount == null) totalAmount = BigDecimal.ZERO;
    if (isBaseAccount == null) isBaseAccount = false;
    if (enabled == null) enabled = true;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
