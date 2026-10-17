package com.jiajunxie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
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
@TableName("bills.b_installment_plans")
@Table(
    name = "b_installment_plans",
    schema = "bills",
    indexes = {
      @Index(name = "idx_installment_plan_account", columnList = "liability_account_id"),
      @Index(name = "idx_installment_plan_status", columnList = "status_code")
    })
public class InstallmentPlan {
  @Id
  @TableId(value = "installment_plan_id", type = IdType.INPUT)
  @Column(name = "installment_plan_id", length = 36, nullable = false)
  private String installmentPlanId;

  @Column(name = "source_transaction_id", length = 36, nullable = false, unique = true)
  private String sourceTransactionId;

  @Column(name = "liability_account_id", length = 36, nullable = false)
  private String liabilityAccountId;

  @Column(name = "payment_type_code", length = 30, nullable = false)
  private String paymentTypeCode;

  @Column(name = "payment_rule_id", length = 36)
  private String paymentRuleId;

  @Column(name = "principal_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal principalAmount;

  @Column(name = "fee_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal feeAmount = BigDecimal.ZERO;

  @Column(name = "currency", length = 10, nullable = false)
  private String currency;

  @Column(name = "installment_count", nullable = false)
  private Integer installmentCount;

  @Column(name = "first_due_date")
  private LocalDate firstDueDate;

  @Column(name = "remainder_rule_code", length = 30, nullable = false)
  private String remainderRuleCode;

  @Column(name = "status_code", length = 30, nullable = false)
  private String statusCode = "ACTIVE";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "source_transaction_id",
      referencedColumnName = "transaction_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_installment_plan_transaction"))
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
      foreignKey = @ForeignKey(name = "fk_installment_plan_account"))
  private Account liabilityAccount;

  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(
      name = "payment_rule_id",
      referencedColumnName = "rule_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_installment_plan_payment_rule"))
  private CreditPaymentRule paymentRule;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (installmentPlanId == null || installmentPlanId.isBlank()) {
      installmentPlanId = UUID.randomUUID().toString();
    }
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
