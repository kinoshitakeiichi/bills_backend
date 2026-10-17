package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AnalyticsPeriodVo(
    LocalDate periodStart,
    LocalDate periodEnd,
    List<CurrencyMetrics> currencies) {

  public record CurrencyMetrics(
      String currency,
      MetricTotals totals,
      List<DimensionMetric> categories,
      List<DimensionMetric> accounts,
      List<DimensionMetric> persons,
      List<DimensionMetric> merchants) {}

  public record MetricTotals(
      BigDecimal income,
      BigDecimal expense,
      BigDecimal netSurplus) {}

  public record DimensionMetric(
      String key,
      String label,
      BigDecimal income,
      BigDecimal expense,
      BigDecimal netSurplus) {}
}
