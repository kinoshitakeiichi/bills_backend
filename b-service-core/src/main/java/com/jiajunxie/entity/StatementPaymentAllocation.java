package com.jiajunxie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@TableName("bills.b_statement_payment_allocations")
@Table(name = "b_statement_payment_allocations", schema = "bills")
public class StatementPaymentAllocation {

  @Id
  @TableId(value = "payment_allocation_id", type = IdType.INPUT)
  @Column(name = "payment_allocation_id", length = 36, nullable = false)
  private String paymentAllocationId;

  @Column(name = "payment_transaction_id", length = 36, nullable = false)
  private String paymentTransactionId;

  @Column(name = "statement_id", length = 36, nullable = false)
  private String statementId;

  @Column(name = "allocated_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal allocatedAmount;

  @Column(name = "currency", length = 10, nullable = false)
  private String currency;

  @Column(name = "allocation_order", nullable = false)
  private Integer allocationOrder = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (paymentAllocationId == null || paymentAllocationId.isBlank()) {
      paymentAllocationId = UUID.randomUUID().toString();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
