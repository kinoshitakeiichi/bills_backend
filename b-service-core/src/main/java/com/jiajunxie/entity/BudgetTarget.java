package com.jiajunxie.entity;

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
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@TableName("bills.b_budget_targets")
@Table(
    name = "b_budget_targets",
    schema = "bills",
    indexes =
        @Index(name = "idx_budget_targets_item", columnList = "budget_item_id,target_type_code"))
public class BudgetTarget {
  @Id
  @TableId(value = "budget_target_id", type = IdType.INPUT)
  @Column(name = "budget_target_id", length = 36, nullable = false)
  private String budgetTargetId;

  @Column(name = "budget_item_id", length = 36, nullable = false)
  private String budgetItemId;

  @Column(name = "target_type_code", length = 30, nullable = false)
  private String targetTypeCode;

  @Column(name = "target_id", length = 36)
  private String targetId;

  @Column(name = "target_value", length = 150)
  private String targetValue;

  @Column(name = "include_descendants", nullable = false)
  private Boolean includeDescendants = false;

  @PrePersist
  void p() {
    if (budgetTargetId == null || budgetTargetId.isBlank())
      budgetTargetId = UUID.randomUUID().toString();
  }

  // Read-only JPA associations: generate database foreign keys while business code continues using
  // scalar IDs.
  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "budget_item_id",
      referencedColumnName = "budget_item_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_budget_targets_item"))
  private BudgetItem budgetItem;
}
