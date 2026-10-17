package com.jiajunxie.entity;

import java.time.Instant;
import java.util.UUID;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

/** 财务分析中的人员维度，不承担登录、认证或权限职责。 */
@Data
@Entity
@Table(
    name = "b_persons",
    schema = "bills",
    indexes = {
      @Index(name = "uk_persons_person_code", columnList = "person_code", unique = true),
      @Index(name = "idx_persons_enabled_sort", columnList = "enabled, sort_order")
    })
@TableName("bills.b_persons")
public class Person {

  @Id
  @TableId(value = "person_id", type = IdType.INPUT)
  @Column(name = "person_id", length = 36, nullable = false, comment = "人员ID")
  private String personId;

  @Column(name = "person_code", length = 30, nullable = false, comment = "稳定业务编码")
  private String personCode;

  @Column(name = "person_name", length = 50, nullable = false, comment = "人员显示名称")
  private String personName;

  @Column(name = "enabled", nullable = false, comment = "是否启用")
  private Boolean enabled = true;

  @Column(name = "sort_order", nullable = false, comment = "显示顺序")
  private Integer sortOrder = 0;

  @Column(name = "created_at", nullable = false, updatable = false, comment = "创建时间")
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false, comment = "更新时间")
  private Instant updatedAt;

  @PrePersist
  public void prePersist() {
    Instant now = Instant.now();
    if (personId == null || personId.isBlank()) {
      personId = UUID.randomUUID().toString();
    }
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  public void preUpdate() {
    updatedAt = Instant.now();
  }
}
