package com.jiajunxie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiajunxie.entity.CurrencyInventoryMovement;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CurrencyInventoryMovementMapper extends BaseMapper<CurrencyInventoryMovement> {

  @Select(
      """
      SELECT count(*)
      FROM bills.b_currency_inventory_movements
      WHERE transaction_id = #{transactionId}
      """)
  long countByTransactionId(@Param("transactionId") String transactionId);

  @Select(
      """
      SELECT *
      FROM bills.b_currency_inventory_movements
      WHERE transaction_id = #{transactionId}
      ORDER BY movement_order DESC
      """)
  List<CurrencyInventoryMovement> selectByTransactionIdReverse(
      @Param("transactionId") String transactionId);

  @Select(
      """
      SELECT count(*)
      FROM bills.b_currency_inventory_movements
      WHERE bucket_id = #{bucketId}
        AND transaction_id <> #{transactionId}
        AND created_at >= #{createdAt}
      """)
  long countOtherMovementsAtOrAfter(
      @Param("bucketId") String bucketId,
      @Param("transactionId") String transactionId,
      @Param("createdAt") Instant createdAt);

  @Select(
      """
      SELECT count(*)
      FROM bills.b_currency_inventory_movements
      WHERE account_id = #{accountId}
        AND created_at >= #{createdAt}
      """)
  long countAccountMovementsAtOrAfter(
      @Param("accountId") String accountId,
      @Param("createdAt") Instant createdAt);

  @Delete(
      """
      DELETE FROM bills.b_currency_inventory_movements
      WHERE transaction_id = #{transactionId}
      """)
  int deleteByTransactionId(@Param("transactionId") String transactionId);
}
