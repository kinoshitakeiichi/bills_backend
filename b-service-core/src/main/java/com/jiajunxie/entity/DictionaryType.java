package com.jiajunxie.entity;

import java.time.Instant;
import java.util.UUID;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Data
@Entity
@TableName("bills.b_dictionary_types")
@Table(
    name = "b_dictionary_types",
    schema = "bills",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_dictionary_type_code", columnNames = "type_code"))
public class DictionaryType {
  @Id
  @TableId(value = "dictionary_type_id", type = IdType.INPUT)
  @Column(name = "dictionary_type_id", length = 36, nullable = false)
  private String dictionaryTypeId;

  @Column(name = "type_code", length = 50, nullable = false)
  private String typeCode;

  @Column(name = "type_name", length = 100, nullable = false)
  private String typeName;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void p() {
    var n = Instant.now();
    if (dictionaryTypeId == null || dictionaryTypeId.isBlank())
      dictionaryTypeId = UUID.randomUUID().toString();
    if (createdAt == null) createdAt = n;
    updatedAt = n;
  }

  @PreUpdate
  void u() {
    updatedAt = Instant.now();
  }
}
