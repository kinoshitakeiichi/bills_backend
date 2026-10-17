package com.jiajunxie.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
@TableName("bills.b_schedule_adjustments")
@Table(
    name = "b_schedule_adjustments",
    schema = "bills",
    indexes = @Index(name = "idx_schedule_adjustments_schedule", columnList = "schedule_id"))
public class ScheduleAdjustment {
  @Id
  @TableId(value = "adjustment_id", type = IdType.INPUT)
  @Column(name = "adjustment_id", length = 36, nullable = false)
  private String adjustmentId;

  @Column(name = "schedule_id", length = 36, nullable = false)
  private String scheduleId;

  @Column(name = "adjustment_type_code", length = 30, nullable = false)
  private String adjustmentTypeCode;

  @Column(name = "before_amount", precision = 19, scale = 4)
  private BigDecimal beforeAmount;

  @Column(name = "after_amount", precision = 19, scale = 4)
  private BigDecimal afterAmount;

  @Column(name = "before_date")
  private LocalDate beforeDate;

  @Column(name = "after_date")
  private LocalDate afterDate;

  @Column(name = "reason", columnDefinition = "text", nullable = false)
  private String reason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void p() {
    if (adjustmentId == null || adjustmentId.isBlank()) adjustmentId = UUID.randomUUID().toString();
    if (createdAt == null) createdAt = Instant.now();
  }

  // Read-only JPA associations: generate database foreign keys while business code continues using
  // scalar IDs.
  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "schedule_id",
      referencedColumnName = "schedule_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_schedule_adjustments_schedule"))
  private PaymentSchedule paymentSchedule;
}
