package com.jiajunxie.entity;

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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@TableName("bills.b_loan_payment_allocations")
@Table(
    name = "b_loan_payment_allocations",
    schema = "bills",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_loan_payment_allocation_pair",
            columnNames = {"loan_payment_transaction_id", "schedule_id"}),
    indexes = {
      @Index(name = "idx_loan_payment_allocation_payment", columnList = "loan_payment_transaction_id"),
      @Index(name = "idx_loan_payment_allocation_schedule", columnList = "schedule_id")
    },
    check = {
      @CheckConstraint(name = "ck_loan_payment_allocation_principal", constraint = "allocated_principal_amount >= 0"),
      @CheckConstraint(name = "ck_loan_payment_allocation_interest", constraint = "allocated_interest_amount >= 0"),
      @CheckConstraint(name = "ck_loan_payment_allocation_fee", constraint = "allocated_fee_amount >= 0"),
      @CheckConstraint(
          name = "ck_loan_payment_allocation_nonzero",
          constraint = "allocated_principal_amount + allocated_interest_amount + allocated_fee_amount > 0")
    })
public class LoanPaymentAllocation {

  @Id
  @TableId(value = "allocation_id", type = IdType.INPUT)
  @Column(name = "allocation_id", length = 36, nullable = false)
  private String allocationId;

  @Column(name = "loan_payment_transaction_id", length = 36, nullable = false)
  private String loanPaymentTransactionId;

  @Column(name = "schedule_id", length = 36, nullable = false)
  private String scheduleId;

  @Column(name = "allocated_principal_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal allocatedPrincipalAmount = BigDecimal.ZERO;

  @Column(name = "allocated_interest_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal allocatedInterestAmount = BigDecimal.ZERO;

  @Column(name = "allocated_fee_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal allocatedFeeAmount = BigDecimal.ZERO;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (allocationId == null || allocationId.isBlank()) {
      allocationId = UUID.randomUUID().toString();
    }
    if (createdAt == null) createdAt = Instant.now();
  }

  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "loan_payment_transaction_id",
      referencedColumnName = "transaction_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_loan_payment_allocation_payment"))
  private Transaction loanPaymentTransaction;

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
      foreignKey = @ForeignKey(name = "fk_loan_payment_allocation_schedule"))
  private PaymentSchedule paymentSchedule;
}
