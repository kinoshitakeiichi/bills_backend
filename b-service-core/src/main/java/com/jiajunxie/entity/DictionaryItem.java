package com.jiajunxie.entity;

import java.time.Instant;
import java.util.UUID;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jiajunxie.typehandler.JsonbStringTypeHandler;
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
@TableName(value = "bills.b_dictionary_items", autoResultMap = true)
@Table(
    name = "b_dictionary_items",
    schema = "bills",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_dictionary_item_code",
            columnNames = {"dictionary_type_id", "item_code"}),
    indexes =
        @Index(
            name = "idx_dictionary_items_type_enabled_sort",
            columnList = "dictionary_type_id,enabled,sort_order"))
public class DictionaryItem {
  @Id
  @TableId(value = "dictionary_item_id", type = IdType.INPUT)
  @Column(name = "dictionary_item_id", length = 36, nullable = false)
  private String dictionaryItemId;

  @Column(name = "dictionary_type_id", length = 36, nullable = false)
  private String dictionaryTypeId;

  @Column(name = "item_code", length = 50, nullable = false)
  private String itemCode;

  @Column(name = "item_name", length = 100, nullable = false)
  private String itemName;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder = 0;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled = true;

  @Column(name = "system_item", nullable = false)
  private Boolean systemItem = false;

  @Column(name = "metadata_json", columnDefinition = "jsonb")
  @TableField(value = "metadata_json", typeHandler = JsonbStringTypeHandler.class)
  private String metadataJson;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void p() {
    var n = Instant.now();
    if (dictionaryItemId == null || dictionaryItemId.isBlank())
      dictionaryItemId = UUID.randomUUID().toString();
    if (createdAt == null) createdAt = n;
    updatedAt = n;
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
      name = "dictionary_type_id",
      referencedColumnName = "dictionary_type_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_dictionary_items_type"))
  private DictionaryType dictionaryType;
}
