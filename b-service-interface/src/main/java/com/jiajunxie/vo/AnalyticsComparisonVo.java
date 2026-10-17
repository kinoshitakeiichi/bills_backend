package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AnalyticsComparisonVo(
    PeriodRange currentPeriod,
    PeriodRange previousPeriod,
    PeriodRange yearOverYearPeriod,
    List<CurrencyComparison> currencies) {

  public record PeriodRange(
      LocalDate start,
      LocalDate end) {}

  public record CurrencyComparison(
      String currency,
      MetricComparison income,
      MetricComparison expense,
      MetricComparison netSurplus) {}

  public record MetricComparison(
      BigDecimal current,
      BaselineComparison previousPeriod,
      BaselineComparison yearOverYear) {}

  public record BaselineComparison(
      BigDecimal baseline,
      BigDecimal delta,
      BigDecimal changeRate) {}
}
