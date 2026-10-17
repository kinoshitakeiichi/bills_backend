package com.jiajunxie.entity;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@TableName("bills.b_credit_card_profiles")
@Table(name = "b_credit_card_profiles", schema = "bills")
public class CreditCardProfile {
  @Id
  @TableId(value = "account_id", type = IdType.INPUT)
  @Column(name = "account_id", length = 36, nullable = false)
  private String accountId;

  @Column(name = "credit_limit", precision = 19, scale = 4)
  private BigDecimal creditLimit;

  /**
   * Business amount precision used by credit-card payment-plan calculation.
   *
   * <p>This is configuration, not a hard-coded currency rule. For example, a JPY card can use 0.
   */
  @Column(name = "amount_scale", nullable = false)
  private Integer amountScale = 0;

  @Column(name = "billing_start_day", nullable = false)
  private Integer billingStartDay;

  @Column(name = "billing_end_day", nullable = false)
  private Integer billingEndDay;

  @Column(name = "billing_end_month_offset", nullable = false)
  private Integer billingEndMonthOffset = 0;

  @Column(name = "confirmation_day")
  private Integer confirmationDay;

  @Column(name = "confirmation_month_offset", nullable = false)
  private Integer confirmationMonthOffset = 0;

  @Column(name = "payment_day", nullable = false)
  private Integer paymentDay;

  @Column(name = "payment_month_offset", nullable = false)
  private Integer paymentMonthOffset = 1;

  @Column(name = "invalid_day_rule_code", length = 30, nullable = false)
  private String invalidDayRuleCode;

  @Column(name = "business_day_adjustment_code", length = 30, nullable = false)
  private String businessDayAdjustmentCode;

  @Column(name = "calendar_code", length = 50)
  private String calendarCode;

  @Column(name = "timezone", length = 50, nullable = false)
  private String timezone;

  @Column(name = "remainder_rule_code", length = 30, nullable = false)
  private String remainderRuleCode;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

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
      foreignKey = @ForeignKey(name = "fk_credit_profiles_account"))
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
      foreignKey = @ForeignKey(name = "fk_credit_profiles_calendar"))
  private BusinessCalendar businessCalendar;
}
