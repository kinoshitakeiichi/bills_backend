package com.jiajunxie.mapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class FxPositionMapperContractTest {

  @Test
  void buyPositionUsesAtomicPostgresUpsertForMovingAverage() throws Exception {
    Method method =
        FxPositionMapper.class.getMethod(
            "upsertBuyPosition",
            String.class,
            String.class,
            String.class,
            java.math.BigDecimal.class,
            java.math.BigDecimal.class,
            String.class);

    Insert insert = method.getAnnotation(Insert.class);
    String sql = String.join("\n", insert.value());

    assertTrue(sql.contains("ON CONFLICT (account_id, currency) DO UPDATE"));
    assertTrue(sql.contains("quantity + EXCLUDED.quantity"));
    assertTrue(sql.contains("total_cost + EXCLUDED.total_cost"));
    assertTrue(sql.contains("average_cost_rate"));
    assertTrue(sql.contains("cost_currency = EXCLUDED.cost_currency"));
  }

  @Test
  void sellPositionKeepsAverageCostForPartialSellAndZerosPositionOnClose() throws Exception {
    Method method =
        FxPositionMapper.class.getMethod(
            "applySellPosition",
            String.class,
            java.math.BigDecimal.class,
            java.math.BigDecimal.class,
            boolean.class);

    Update update = method.getAnnotation(Update.class);
    String sql = String.join("\n", update.value());

    assertTrue(sql.contains("quantity = CASE WHEN #{closePosition} THEN 0 ELSE quantity - #{disposedQuantity} END"));
    assertTrue(sql.contains("total_cost = CASE WHEN #{closePosition} THEN 0 ELSE total_cost - #{costBasisAmount} END"));
    assertTrue(sql.contains("average_cost_rate = CASE WHEN #{closePosition} THEN 0 ELSE average_cost_rate END"));
    assertTrue(sql.contains("quantity >= #{disposedQuantity}"));
  }

@Test
void exposesSystemWeightedAverageRateQuery() throws Exception {
  var method =
      FxPositionMapper.class.getMethod(
          "selectSystemWeightedAverageRate", String.class, String.class);
  assertNotNull(method);
}

}
