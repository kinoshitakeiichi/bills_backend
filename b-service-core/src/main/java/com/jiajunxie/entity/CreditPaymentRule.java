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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@TableName("bills.b_credit_payment_rules")
@Table(
    name = "b_credit_payment_rules",
    schema = "bills",
    indexes = @Index(name = "idx_credit_rules_account", columnList = "account_id,enabled,priority"))
public class CreditPaymentRule {
  @Id
  @TableId(value = "rule_id", type = IdType.INPUT)
  @Column(name = "rule_id", length = 36, nullable = false)
  private String ruleId;

  @Column(name = "account_id", length = 36, nullable = false)
  private String accountId;

  @Column(name = "rule_type_code", length = 30, nullable = false)
  private String ruleTypeCode;

  @Column(name = "purchase_start_month_day", length = 5, nullable = false)
  private String purchaseStartMonthDay;

  @Column(name = "purchase_end_month_day", length = 5, nullable = false)
  private String purchaseEndMonthDay;

  @Column(name = "target_month", nullable = false)
  private Integer targetMonth;

  @Column(name = "target_day", nullable = false)
  private Integer targetDay;

  @Column(name = "priority", nullable = false)
  private Integer priority = 0;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (ruleId == null || ruleId.isBlank()) ruleId = UUID.randomUUID().toString();
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
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "account_id",
      referencedColumnName = "account_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_credit_rules_profile"))
  private CreditCardProfile creditCardProfile;
}
