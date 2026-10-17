package com.jiajunxie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

@Data
@Entity
@TableName("bills.b_loan_payment_details")
@Table(name = "b_loan_payment_details", schema = "bills")
public class LoanPaymentDetail {
  @Id
  @TableId(value = "transaction_id", type = IdType.INPUT)
  @Column(name = "transaction_id", length = 36, nullable = false)
  private String transactionId;

  @Column(name = "loan_account_id", length = 36, nullable = false)
  private String loanAccountId;

  @Column(name = "principal_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal principalAmount;

  @Column(name = "interest_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal interestAmount;

  @Column(name = "fee_amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal feeAmount;

  @Column(name = "currency", length = 10, nullable = false)
  private String currency;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
