package com.jiajunxie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiajunxie.entity.CurrencyInventoryBucket;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CurrencyInventoryBucketMapper extends BaseMapper<CurrencyInventoryBucket> {

  @Select(
      """
      SELECT *
      FROM bills.b_currency_inventory_buckets
      WHERE account_id = #{accountId}
        AND origin_type_code = 'FX_EXCHANGE'
        AND quantity > 0
      ORDER BY source_currency, bucket_id
      """)
  List<CurrencyInventoryBucket> selectFxByAccount(@Param("accountId") String accountId);

  /**
   * System-wide weighted average direct cost for a currency pair after V3 cutover.
   *
   * <p>For USD/JPY, direct buckets are USD holdings whose original source cost is JPY:
   * SUM(source_cost_amount JPY) / SUM(quantity USD). If only reverse JPY/USD buckets exist,
   * return the reciprocal weighted rate.
   */
  @Select(
      """
      WITH direct_bucket AS (
        SELECT
          SUM(source_cost_amount) AS total_cost,
          SUM(quantity) AS total_quantity
        FROM bills.b_currency_inventory_buckets
        WHERE origin_type_code = 'FX_EXCHANGE'
          AND UPPER(currency) = UPPER(#{baseCurrency})
          AND UPPER(source_currency) = UPPER(#{quoteCurrency})
          AND quantity > 0
      ),
      reverse_bucket AS (
        SELECT
          SUM(source_cost_amount) AS total_cost,
          SUM(quantity) AS total_quantity
        FROM bills.b_currency_inventory_buckets
        WHERE origin_type_code = 'FX_EXCHANGE'
          AND UPPER(currency) = UPPER(#{quoteCurrency})
          AND UPPER(source_currency) = UPPER(#{baseCurrency})
          AND quantity > 0
      )
      SELECT COALESCE(
        CASE
          WHEN direct_bucket.total_quantity > 0
          THEN ROUND(direct_bucket.total_cost / direct_bucket.total_quantity, 10)
        END,
        CASE
          WHEN reverse_bucket.total_cost > 0
          THEN ROUND(reverse_bucket.total_quantity / reverse_bucket.total_cost, 10)
        END
      )
      FROM direct_bucket
      CROSS JOIN reverse_bucket
      """)
  BigDecimal selectSystemWeightedAverageRate(
      @Param("baseCurrency") String baseCurrency,
      @Param("quoteCurrency") String quoteCurrency);

  @Select(
      """
      SELECT *
      FROM bills.b_currency_inventory_buckets
      WHERE bucket_id = #{bucketId}
      FOR UPDATE
      """)
  CurrencyInventoryBucket selectByIdForUpdate(@Param("bucketId") String bucketId);

  @Select(
      """
      SELECT *
      FROM bills.b_currency_inventory_buckets
      WHERE account_id = #{accountId}
        AND origin_type_code = 'FX_EXCHANGE'
      ORDER BY source_currency, bucket_id
      FOR UPDATE
      """)
  List<CurrencyInventoryBucket> selectFxByAccountForUpdate(@Param("accountId") String accountId);

  @Select(
      """
      SELECT *
      FROM bills.b_currency_inventory_buckets
      WHERE account_id = #{accountId}
        AND origin_type_code = 'FX_EXCHANGE'
        AND UPPER(source_currency) = UPPER(#{sourceCurrency})
      FOR UPDATE
      """)
  CurrencyInventoryBucket selectFxByAccountAndSourceForUpdate(
      @Param("accountId") String accountId,
      @Param("sourceCurrency") String sourceCurrency);

  @Update(
      """
      UPDATE bills.b_currency_inventory_buckets
      SET quantity = #{quantity},
          source_cost_amount = #{sourceCostAmount},
          average_cost_rate = #{averageCostRate},
          updated_at = #{updatedAt}
      WHERE bucket_id = #{bucketId}
      """)
  int updateAmounts(
      @Param("bucketId") String bucketId,
      @Param("quantity") BigDecimal quantity,
      @Param("sourceCostAmount") BigDecimal sourceCostAmount,
      @Param("averageCostRate") BigDecimal averageCostRate,
      @Param("updatedAt") Instant updatedAt);
}
