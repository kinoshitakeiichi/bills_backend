package com.jiajunxie.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AnalyticsPeriodResponse(
    LocalDate periodStart,
    LocalDate periodEnd,
    List<CurrencyMetricsResponse> currencies) {

  public record CurrencyMetricsResponse(
      String currency,
      MetricTotalsResponse totals,
      List<DimensionMetricResponse> categories,
      List<DimensionMetricResponse> accounts,
      List<DimensionMetricResponse> persons,
      List<DimensionMetricResponse> merchants) {}

  public record MetricTotalsResponse(
      BigDecimal income,
      BigDecimal expense,
      BigDecimal netSurplus) {}

  public record DimensionMetricResponse(
      String key,
      String label,
      BigDecimal income,
      BigDecimal expense,
      BigDecimal netSurplus) {}
}
