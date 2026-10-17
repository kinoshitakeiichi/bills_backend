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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@TableName("bills.b_loan_repayment_schedule_details")
@Table(
    name = "b_loan_repayment_schedule_details",
    schema = "bills",
    indexes = {
      @Index(name = "idx_loan_schedule_detail_account", columnList = "loan_account_id"),
      @Index(name = "idx_loan_schedule_detail_generation", columnList = "loan_account_id,generation_no")
    },
    check = {
      @CheckConstraint(name = "ck_loan_schedule_detail_interest", constraint = "scheduled_interest_amount >= 0"),
      @CheckConstraint(name = "ck_loan_schedule_detail_remaining_principal", constraint = "remaining_principal_after >= 0"),
      @CheckConstraint(name = "ck_loan_schedule_detail_rate", constraint = "annual_rate_snapshot >= 0"),
      @CheckConstraint(name = "ck_loan_schedule_detail_generation", constraint = "generation_no >= 1"),
      @CheckConstraint(name = "ck_loan_schedule_detail_amount_scale", constraint = "amount_scale BETWEEN 0 AND 4"),
      @CheckConstraint(name = "ck_loan_schedule_manual_principal", constraint = "manual_principal_amount IS NULL OR manual_principal_amount >= 0"),
      @CheckConstraint(name = "ck_loan_schedule_manual_interest", constraint = "manual_interest_amount IS NULL OR manual_interest_amount >= 0"),
      @CheckConstraint(name = "ck_loan_schedule_manual_fee", constraint = "manual_fee_amount IS NULL OR manual_fee_amount >= 0")
    })
public class LoanRepaymentScheduleDetail {

  @Id
  @TableId(value = "schedule_id", type = IdType.INPUT)
  @Column(name = "schedule_id", length = 36, nullable = false)
  private String scheduleId;

  @Column(name = "loan_account_id", length = 36, nullable = false)
  private String loanAccountId;

  @Column(name = "repayment_type_code", length = 30, nullable = false)
  private String repaymentTypeCode;

  @Column(name = "scheduled_interest_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal scheduledInterestAmount = BigDecimal.ZERO;

  @Column(name = "manual_principal_amount", precision = 19, scale = 4)
  private BigDecimal manualPrincipalAmount;

  @Column(name = "manual_interest_amount", precision = 19, scale = 4)
  private BigDecimal manualInterestAmount;

  @Column(name = "manual_fee_amount", precision = 19, scale = 4)
  private BigDecimal manualFeeAmount;

  @Column(name = "remaining_principal_after", precision = 19, scale = 4, nullable = false)
  private BigDecimal remainingPrincipalAfter;

  @Column(name = "annual_rate_snapshot", precision = 12, scale = 8, nullable = false)
  private BigDecimal annualRateSnapshot = BigDecimal.ZERO;

  @Column(name = "rate_effective_from")
  private LocalDate rateEffectiveFrom;

  @Column(name = "remainder_rule_code", length = 30, nullable = false)
  private String remainderRuleCode;

  @Column(name = "adjustment_mode_code", length = 30, nullable = false)
  private String adjustmentModeCode;

  @Column(name = "calculation_version", length = 30, nullable = false)
  private String calculationVersion;

  @Column(name = "amount_scale", nullable = false)
  private Integer amountScale;

  @Column(name = "generation_no", nullable = false)
  private Integer generationNo = 1;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = Instant.now();
  }

  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "schedule_id",
      referencedColumnName = "schedule_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_loan_schedule_detail_schedule"))
  private PaymentSchedule paymentSchedule;

  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "loan_account_id",
      referencedColumnName = "account_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_loan_schedule_detail_account"))
  private Account loanAccount;
}
