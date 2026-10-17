package com.jiajunxie.entity;

import java.time.Instant;
import java.time.LocalDate;
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

@Data
@Entity
@TableName("bills.b_budgets")
@Table(
    name = "b_budgets",
    schema = "bills",
    indexes = @Index(name = "idx_budgets_period", columnList = "period_start,period_end,enabled"))
public class Budget {
  @Id
  @TableId(value = "budget_id", type = IdType.INPUT)
  @Column(name = "budget_id", length = 36, nullable = false)
  private String budgetId;

  @Column(name = "budget_name", length = 100, nullable = false)
  private String budgetName;

  @Column(name = "period_type_code", length = 30, nullable = false)
  private String periodTypeCode;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Column(name = "base_currency", length = 10, nullable = false)
  private String baseCurrency;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void p() {
    var n = Instant.now();
    if (budgetId == null || budgetId.isBlank()) budgetId = UUID.randomUUID().toString();
    if (createdAt == null) createdAt = n;
    updatedAt = n;
  }

  @PreUpdate
  void u() {
    updatedAt = Instant.now();
  }
}
