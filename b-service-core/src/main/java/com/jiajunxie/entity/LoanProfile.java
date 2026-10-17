package com.jiajunxie.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@TableName("bills.b_loan_profiles")
@Table(
    name = "b_loan_profiles",
    schema = "bills",
    check =
        @CheckConstraint(
            name = "ck_principal_rate",
            constraint = "annual_rate >= 0 AND ((funding_mode_code = 'PURCHASE_FINANCING' AND original_principal IS NULL) OR (funding_mode_code IN ('ACCOUNT_DISBURSEMENT', 'DIRECT_FINANCING') AND original_principal > 0))"))
public class LoanProfile {
  @Id
  @TableId(value = "account_id", type = IdType.INPUT)
  @Column(name = "account_id", length = 36, nullable = false)
  private String accountId;

  @Column(name = "loan_type_code", length = 30, nullable = false)
  private String loanTypeCode;

  @Column(name = "funding_mode_code", length = 30, nullable = false)
  private String fundingModeCode;

  @Column(name = "original_principal", precision = 19, scale = 4)
  private BigDecimal originalPrincipal;

  @Column(name = "currency", length = 10, nullable = false)
  private String currency;

  @Column(name = "rate_type_code", length = 30, nullable = false)
  private String rateTypeCode;

  @Column(name = "annual_rate", precision = 12, scale = 8, nullable = false)
  private BigDecimal annualRate;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "maturity_date")
  private LocalDate maturityDate;

  @Column(name = "payment_frequency_code", length = 30, nullable = false)
  private String paymentFrequencyCode;

  @Column(name = "payment_day")
  private Integer paymentDay;

  @Column(name = "calendar_code", length = 50)
  private String calendarCode;

  @Column(name = "business_day_adjustment_code", length = 30)
  private String businessDayAdjustmentCode;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void p() {
    var n = Instant.now();
    if (createdAt == null) createdAt = n;
    updatedAt = n;
  }

  @PreUpdate
  void u() {
    updatedAt = Instant.now();
  }

  // Read-only JPA associations: generate database foreign keys while business code continues using
  // scalar IDs.
  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "account_id",
      referencedColumnName = "account_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_loan_profiles_account"))
  private Account account;

  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(
      name = "calendar_code",
      referencedColumnName = "calendar_code",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_loan_profiles_calendar"))
  private BusinessCalendar businessCalendar;
}
