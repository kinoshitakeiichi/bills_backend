package com.jiajunxie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jiajunxie.typehandler.JsonbStringTypeHandler;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@TableName(value = "bills.b_account_transaction_templates", autoResultMap = true)
@Table(name = "b_account_transaction_templates", schema = "bills")
public class AccountTransactionTemplate {
  @Id
  @TableId(value = "template_id", type = IdType.INPUT)
  @Column(name = "template_id", length = 36, nullable = false)
  private String templateId;

  @Column(name = "account_id", length = 36, nullable = false)
  private String accountId;

  @Column(name = "template_name", length = 100, nullable = false)
  private String templateName;

  @Column(name = "preset_json", columnDefinition = "jsonb", nullable = false)
  @TableField(value = "preset_json", typeHandler = JsonbStringTypeHandler.class)
  private String presetJson;

  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder = 0;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (templateId == null || templateId.isBlank()) templateId = UUID.randomUUID().toString();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
