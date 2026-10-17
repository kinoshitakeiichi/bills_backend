package com.jiajunxie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiajunxie.entity.FxPositionMovement;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FxPositionMovementMapper extends BaseMapper<FxPositionMovement> {

  @Select(
      """
      SELECT movement_id, transaction_id, account_id, movement_order, movement_type_code,
             currency, quantity_delta, cost_delta, cost_currency, valuation_rate,
             realized_gain_loss, gain_loss_currency, created_at
      FROM bills.b_fx_position_movements
      WHERE transaction_id = #{transactionId}
      ORDER BY movement_order DESC
      """)
  List<FxPositionMovement> selectByTransactionIdReverse(@Param("transactionId") String transactionId);

  @Delete("DELETE FROM bills.b_fx_position_movements WHERE account_id = #{accountId}")
  int deleteByAccountId(@Param("accountId") String accountId);

  @Delete(
      """
      DELETE FROM bills.b_fx_position_movements
      WHERE transaction_id = #{transactionId}
      """)
  int deleteByTransactionId(@Param("transactionId") String transactionId);
  @Select(
      """
      SELECT COUNT(*)
      FROM bills.b_fx_position_movements
      WHERE account_id = #{accountId}
        AND transaction_id <> #{transactionId}
        AND created_at > #{createdAt}
      """)
  long countLaterMovements(
      @Param("accountId") String accountId,
      @Param("transactionId") String transactionId,
      @Param("createdAt") java.time.Instant createdAt);

  @Select("SELECT COUNT(*) FROM bills.b_fx_position_movements WHERE transaction_id = #{transactionId}")
  long countByTransactionId(@Param("transactionId") String transactionId);

}
