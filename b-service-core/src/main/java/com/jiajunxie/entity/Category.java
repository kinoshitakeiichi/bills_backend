package com.jiajunxie.entity;

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
@TableName("bills.b_categories")
@Table(
    name = "b_categories",
    schema = "bills",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_category_code", columnNames = "category_code"),
      @UniqueConstraint(
          name = "uk_category_sibling",
          columnNames = {"parent_key", "category_name"})
    },
    indexes = {
      @Index(name = "idx_categories_parent", columnList = "parent_id"),
      @Index(name = "idx_categories_type_enabled", columnList = "category_type_code,enabled")
    })
public class Category {
  @Id
  @TableId(value = "category_id", type = IdType.INPUT)
  @Column(name = "category_id", length = 36, nullable = false)
  private String categoryId;

  @Column(name = "category_code", length = 50, nullable = false)
  private String categoryCode;

  @Column(name = "category_name", length = 100, nullable = false)
  private String categoryName;

  @Column(name = "category_type_code", length = 20, nullable = false)
  private String categoryTypeCode;

  @Column(name = "icon_code", length = 64)
  private String iconCode;

  @Column(name = "parent_id", length = 36)
  private String parentId;

  @Column(name = "parent_key", length = 36, nullable = false)
  private String parentKey = "ROOT";

  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder = 0;

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
  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(
      name = "parent_id",
      referencedColumnName = "category_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_categories_parent"))
  private Category parentCategory;

  @PrePersist
  void prePersist() {
    var now = Instant.now();
    if (categoryId == null || categoryId.isBlank()) categoryId = UUID.randomUUID().toString();
    if (parentKey == null || parentKey.isBlank()) parentKey = parentId == null ? "ROOT" : parentId;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
