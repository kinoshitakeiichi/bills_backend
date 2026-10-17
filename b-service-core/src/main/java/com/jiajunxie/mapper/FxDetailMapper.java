package com.jiajunxie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiajunxie.entity.FxDetail;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FxDetailMapper extends BaseMapper<FxDetail> {

  /** Returns the source-amount-weighted executed rate for one calendar day and pair direction. */
  @Select(
      """
      SELECT ROUND(SUM(d.target_amount) / NULLIF(SUM(d.source_amount), 0), 10)
      FROM bills.b_fx_details d
      JOIN bills.b_transactions t ON t.transaction_id = d.transaction_id
      WHERE (t.transaction_time AT TIME ZONE 'Asia/Tokyo')::date = #{rateDate}
        AND d.source_currency = #{sourceCurrency}
        AND d.target_currency = #{targetCurrency}
        AND d.source_amount > 0
        AND d.target_amount > 0
      """)
  BigDecimal selectDailyWeightedExecutedRate(
      @Param("rateDate") LocalDate rateDate,
      @Param("sourceCurrency") String sourceCurrency,
      @Param("targetCurrency") String targetCurrency);
}

