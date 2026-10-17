package com.jiajunxie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@TableName("bills.b_statement_adjustments")
@Table(name = "b_statement_adjustments", schema = "bills")
public class StatementAdjustment {

  @Id
  @TableId(value = "statement_adjustment_id", type = IdType.INPUT)
  @Column(name = "statement_adjustment_id", length = 36, nullable = false)
  private String statementAdjustmentId;

  @Column(name = "statement_id", length = 36, nullable = false)
  private String statementId;

  @Column(name = "adjustment_type_code", length = 30, nullable = false)
  private String adjustmentTypeCode;

  @Column(name = "amount", precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(name = "currency", length = 10)
  private String currency;

  @Column(name = "old_payment_date")
  private LocalDate oldPaymentDate;

  @Column(name = "new_payment_date")
  private LocalDate newPaymentDate;

  @Column(name = "reason", columnDefinition = "text")
  private String reason;

  @Column(name = "status_code", length = 30, nullable = false)
  private String statusCode = "ACTIVE";

  @Column(name = "linked_transaction_id", length = 36)
  private String linkedTransactionId;

  @Column(name = "reversal_transaction_id", length = 36)
  private String reversalTransactionId;

  @Column(name = "reversed_at")
  private Instant reversedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (statementAdjustmentId == null || statementAdjustmentId.isBlank()) {
      statementAdjustmentId = UUID.randomUUID().toString();
    }
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
