package com.jiajunxie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@TableName("bills.b_credit_card_statements")
@Table(name = "b_credit_card_statements", schema = "bills")
public class CreditCardStatement {
  @Id @TableId(value = "statement_id", type = IdType.INPUT)
  @Column(name = "statement_id", length = 36, nullable = false) private String statementId;
  @Column(name = "credit_account_id", length = 36, nullable = false) private String creditAccountId;
  @Column(name = "statement_number", length = 80, nullable = false) private String statementNumber;
  @Column(name = "statement_year", nullable = false) private Integer statementYear;
  @Column(name = "statement_month", nullable = false) private Integer statementMonth;
  @Column(name = "period_start", nullable = false) private LocalDate periodStart;
  @Column(name = "period_end", nullable = false) private LocalDate periodEnd;
  @Column(name = "confirmation_date") private LocalDate confirmationDate;
  @Column(name = "scheduled_payment_date", nullable = false) private LocalDate scheduledPaymentDate;
  @Column(name = "calculated_payment_date", nullable = false) private LocalDate calculatedPaymentDate;
  @Column(name = "manual_payment_date") private LocalDate manualPaymentDate;
  @Column(name = "principal_amount", precision = 19, scale = 4, nullable = false) private BigDecimal principalAmount = BigDecimal.ZERO;
  @Column(name = "fee_amount", precision = 19, scale = 4, nullable = false) private BigDecimal feeAmount = BigDecimal.ZERO;
  @Column(name = "adjustment_amount", precision = 19, scale = 4, nullable = false) private BigDecimal adjustmentAmount = BigDecimal.ZERO;
  @Column(name = "total_amount", precision = 19, scale = 4, nullable = false) private BigDecimal totalAmount = BigDecimal.ZERO;
  @Column(name = "paid_amount", precision = 19, scale = 4, nullable = false) private BigDecimal paidAmount = BigDecimal.ZERO;
  @Column(name = "currency", length = 10, nullable = false) private String currency;
  @Column(name = "status_code", length = 30, nullable = false) private String statusCode = "OPEN";
  @Column(name = "confirmed_at") private Instant confirmedAt;
  @Column(name = "reopened_at") private Instant reopenedAt;
  @Column(name = "reopen_reason", columnDefinition = "text") private String reopenReason;
  @Column(name = "reopen_count", nullable = false) private Integer reopenCount = 0;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @PrePersist void prePersist(){var now=Instant.now();if(statementId==null||statementId.isBlank())statementId=UUID.randomUUID().toString();if(createdAt==null)createdAt=now;updatedAt=now;}
  @PreUpdate void preUpdate(){updatedAt=Instant.now();}
}
