package com.jiajunxie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiajunxie.entity.FxPosition;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FxPositionMapper extends BaseMapper<FxPosition> {

  /**
   * Atomically creates or increments one (account_id, currency) position.
   *
   * <p>The database unique key serializes concurrent BUYs. The conditional update also prevents
   * mixing different cost currencies into one moving-average position.
   */
  @Insert(
      """
      INSERT INTO bills.b_fx_positions
      (
        position_id,
        account_id,
        currency,
        quantity,
        total_cost,
        cost_currency,
        average_cost_rate,
        updated_at
      )
      VALUES
      (
        #{positionId},
        #{accountId},
        #{currency},
        #{quantityDelta},
        #{costDelta},
        #{costCurrency},
        ROUND(#{costDelta} / #{quantityDelta}, 10),
        CURRENT_TIMESTAMP
      )
      ON CONFLICT (account_id, currency) DO UPDATE
      SET quantity =
            bills.b_fx_positions.quantity + EXCLUDED.quantity,
          total_cost =
            bills.b_fx_positions.total_cost + EXCLUDED.total_cost,
          average_cost_rate =
            ROUND(
              (bills.b_fx_positions.total_cost + EXCLUDED.total_cost)
              /
              (bills.b_fx_positions.quantity + EXCLUDED.quantity),
              10
            ),
          updated_at = CURRENT_TIMESTAMP
      WHERE bills.b_fx_positions.cost_currency = EXCLUDED.cost_currency
      """)
  int upsertBuyPosition(
      @Param("positionId") String positionId,
      @Param("accountId") String accountId,
      @Param("currency") String currency,
      @Param("quantityDelta") BigDecimal quantityDelta,
      @Param("costDelta") BigDecimal costDelta,
      @Param("costCurrency") String costCurrency);

  @Select(
      """
      SELECT position_id, account_id, currency, quantity, total_cost, cost_currency,
             average_cost_rate, updated_at
      FROM bills.b_fx_positions
      WHERE account_id = #{accountId}
      FOR UPDATE
      """)
  FxPosition selectByAccountIdForUpdate(@Param("accountId") String accountId);

  @Update(
      """
      UPDATE bills.b_fx_positions
      SET quantity = CASE WHEN #{closePosition} THEN 0 ELSE quantity - #{disposedQuantity} END,
          total_cost = CASE WHEN #{closePosition} THEN 0 ELSE total_cost - #{costBasisAmount} END,
          average_cost_rate = CASE WHEN #{closePosition} THEN 0 ELSE average_cost_rate END,
          updated_at = CURRENT_TIMESTAMP
      WHERE account_id = #{accountId}
        AND quantity >= #{disposedQuantity}
      """)
  int applySellPosition(
      @Param("accountId") String accountId,
      @Param("disposedQuantity") BigDecimal disposedQuantity,
      @Param("costBasisAmount") BigDecimal costBasisAmount,
      @Param("closePosition") boolean closePosition);

@Select(
    """
    SELECT position_id,
           account_id,
           currency,
           quantity,
           total_cost,
           cost_currency,
           average_cost_rate,
           updated_at
    FROM bills.b_fx_positions
    WHERE account_id = #{accountId}
    """)
FxPosition selectByAccountId(@Param("accountId") String accountId);


/**
 * Returns the system-wide weighted average cost rate in the requested pair direction.
 *
 * <p>Direct example USD/JPY:
 * SUM(total_cost JPY) / SUM(quantity USD).
 *
 * <p>If only the reverse position direction exists, the reciprocal weighted rate is returned.
 * Closed positions (quantity <= 0) are ignored. Null means the system has no usable position
 * cost for the requested pair.
 */
@Select(
    """
    WITH direct_position AS (
      SELECT
        SUM(total_cost) AS total_cost,
        SUM(quantity) AS total_quantity
      FROM bills.b_fx_positions
      WHERE currency = #{baseCurrency}
        AND cost_currency = #{quoteCurrency}
        AND quantity > 0
    ),
    reverse_position AS (
      SELECT
        SUM(total_cost) AS total_cost,
        SUM(quantity) AS total_quantity
      FROM bills.b_fx_positions
      WHERE currency = #{quoteCurrency}
        AND cost_currency = #{baseCurrency}
        AND quantity > 0
    )
    SELECT COALESCE(
      CASE
        WHEN direct_position.total_quantity > 0
        THEN ROUND(direct_position.total_cost / direct_position.total_quantity, 10)
      END,
      CASE
        WHEN reverse_position.total_cost > 0
        THEN ROUND(reverse_position.total_quantity / reverse_position.total_cost, 10)
      END
    )
    FROM direct_position
    CROSS JOIN reverse_position
    """)
BigDecimal selectSystemWeightedAverageRate(
    @Param("baseCurrency") String baseCurrency,
    @Param("quoteCurrency") String quoteCurrency);


}
