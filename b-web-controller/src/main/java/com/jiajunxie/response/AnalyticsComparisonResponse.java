package com.jiajunxie.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AnalyticsComparisonResponse(
    PeriodRangeResponse currentPeriod,
    PeriodRangeResponse previousPeriod,
    PeriodRangeResponse yearOverYearPeriod,
    List<CurrencyComparisonResponse> currencies) {

  public record PeriodRangeResponse(
      LocalDate start,
      LocalDate end) {}

  public record CurrencyComparisonResponse(
      String currency,
      MetricComparisonResponse income,
      MetricComparisonResponse expense,
      MetricComparisonResponse netSurplus) {}

  public record MetricComparisonResponse(
      BigDecimal current,
      BaselineComparisonResponse previousPeriod,
      BaselineComparisonResponse yearOverYear) {}

  public record BaselineComparisonResponse(
      BigDecimal baseline,
      BigDecimal delta,
      BigDecimal changeRate) {}
}
