package com.jiajunxie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@TableName("bills.b_payment_schedules")
@Table(
    name = "b_payment_schedules",
    schema = "bills",
    indexes = {
      @Index(name = "idx_payment_schedule_account_status", columnList = "liability_account_id,status_code"),
      @Index(name = "idx_payment_schedule_billing_date", columnList = "scheduled_billing_date"),
      @Index(name = "idx_payment_schedule_payment_date", columnList = "calculated_payment_date")
    },
    check =
        @CheckConstraint(
            name = "ck_payment_schedule_total_nonzero",
            constraint =
                "payment_type_code = 'LOAN' OR scheduled_principal_amount + scheduled_fee_amount + adjustment_amount <> 0"))
public class PaymentSchedule {
  @Id
  @TableId(value = "schedule_id", type = IdType.INPUT)
  @Column(name = "schedule_id", length = 36, nullable = false)
  private String scheduleId;

  @Column(name = "installment_plan_id", length = 36, nullable = false)
  private String installmentPlanId;

  @Column(name = "source_transaction_id", length = 36, nullable = false)
  private String sourceTransactionId;

  @Column(name = "liability_account_id", length = 36, nullable = false)
  private String liabilityAccountId;

  @Column(name = "installment_no", nullable = false)
  private Integer installmentNo;

  @Column(name = "installment_count", nullable = false)
  private Integer installmentCount;

  @Column(name = "scheduled_principal_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal scheduledPrincipalAmount;

  @Column(name = "scheduled_fee_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal scheduledFeeAmount = BigDecimal.ZERO;

  @Column(name = "adjustment_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal adjustmentAmount = BigDecimal.ZERO;

  @Column(name = "currency", length = 10, nullable = false)
  private String currency;

  @Column(name = "payment_type_code", length = 30, nullable = false)
  private String paymentTypeCode;

  @Column(name = "scheduled_billing_date", nullable = false)
  private LocalDate scheduledBillingDate;

  @Column(name = "scheduled_payment_date", nullable = false)
  private LocalDate scheduledPaymentDate;

  @Column(name = "calculated_payment_date", nullable = false)
  private LocalDate calculatedPaymentDate;

  @Column(name = "manual_payment_date")
  private LocalDate manualPaymentDate;

  @Column(name = "status_code", length = 30, nullable = false)
  private String statusCode = "PENDING";

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
      name = "installment_plan_id",
      referencedColumnName = "installment_plan_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_payment_schedule_plan"))
  private InstallmentPlan installmentPlan;

  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "source_transaction_id",
      referencedColumnName = "transaction_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_payment_schedule_transaction"))
  private Transaction sourceTransaction;

  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "liability_account_id",
      referencedColumnName = "account_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_payment_schedule_account"))
  private Account liabilityAccount;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (scheduleId == null || scheduleId.isBlank()) scheduleId = UUID.randomUUID().toString();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
