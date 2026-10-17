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
@TableName("bills.b_fx_position_movements")
@Table(name = "b_fx_position_movements", schema = "bills")
public class FxPositionMovement {
  @Id
  @TableId(value = "movement_id", type = IdType.INPUT)
  @Column(name = "movement_id", length = 36, nullable = false)
  private String movementId;

  @Column(name = "transaction_id", length = 36, nullable = false)
  private String transactionId;

  @Column(name = "account_id", length = 36, nullable = false)
  private String accountId;

  @Column(name = "movement_order", nullable = false)
  private Integer movementOrder;

  @Column(name = "movement_type_code", length = 30, nullable = false)
  private String movementTypeCode;

  @Column(name = "currency", length = 10, nullable = false)
  private String currency;

  @Column(name = "quantity_delta", precision = 19, scale = 4, nullable = false)
  private BigDecimal quantityDelta;

  @Column(name = "cost_delta", precision = 19, scale = 4, nullable = false)
  private BigDecimal costDelta;

  @Column(name = "cost_currency", length = 10, nullable = false)
  private String costCurrency;

  @Column(name = "valuation_rate", precision = 24, scale = 10)
  private BigDecimal valuationRate;

  @Column(name = "realized_gain_loss", precision = 19, scale = 4)
  private BigDecimal realizedGainLoss;

  @Column(name = "gain_loss_currency", length = 10)
  private String gainLossCurrency;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
