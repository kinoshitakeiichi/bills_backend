package com.jiajunxie.entity;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Structured master-data relationship between ACCOUNT_TYPE and ACCOUNT_CLASS dictionary items.
 *
 * <p>The relationship is intentionally not stored in DictionaryItem.metadataJson because the
 * account class determines accounting semantics. A dedicated table gives the relationship explicit
 * foreign keys, a stable schema and independent validation.
 */
@Data
@Entity
@TableName("bills.b_account_type_class_mappings")
@Table(name = "b_account_type_class_mappings", schema = "bills")
public class AccountTypeClassMapping {

  /** One ACCOUNT_TYPE item can have exactly one class mapping. */
  @Id
  @TableId("account_type_item_id")
  @Column(name = "account_type_item_id", length = 36, nullable = false)
  private String accountTypeItemId;

  @Column(name = "account_class_item_id", length = 36, nullable = false)
  private String accountClassItemId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "account_type_item_id",
      referencedColumnName = "dictionary_item_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_account_type_mapping_type_item"))
  private DictionaryItem accountTypeItem;

  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "account_class_item_id",
      referencedColumnName = "dictionary_item_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_account_type_mapping_class_item"))
  private DictionaryItem accountClassItem;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
