package com.jiajunxie.entity;

import java.math.BigDecimal;
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
@TableName("bills.b_budget_items")
@Table(
    name = "b_budget_items",
    schema = "bills",
    indexes = @Index(name = "idx_budget_items_budget", columnList = "budget_id"),
    check = @CheckConstraint(name = "ck_budget_items_amount", constraint = "budget_amount >= 0"))
public class BudgetItem {
  @Id
  @TableId(value = "budget_item_id", type = IdType.INPUT)
  @Column(name = "budget_item_id", length = 36, nullable = false)
  private String budgetItemId;

  @Column(name = "budget_id", length = 36, nullable = false)
  private String budgetId;

  @Column(name = "budget_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal budgetAmount;

  @Column(name = "currency", length = 10, nullable = false)
  private String currency;

  @Column(name = "rollover_enabled", nullable = false)
  private Boolean rolloverEnabled = false;

  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder = 0;

  @PrePersist
  void p() {
    if (budgetItemId == null || budgetItemId.isBlank()) budgetItemId = UUID.randomUUID().toString();
  }

  // Read-only JPA associations: generate database foreign keys while business code continues using
  // scalar IDs.
  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "budget_id",
      referencedColumnName = "budget_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_budget_items_budget"))
  private Budget budget;
}
