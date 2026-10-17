package com.jiajunxie.convert;

import com.jiajunxie.response.AnalyticsPeriodResponse;
import com.jiajunxie.vo.AnalyticsPeriodVo;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsConvertWebMapper {

  public AnalyticsPeriodResponse toResponse(AnalyticsPeriodVo source) {
    return new AnalyticsPeriodResponse(
        source.periodStart(),
        source.periodEnd(),
        source.currencies().stream().map(this::toCurrencyResponse).toList());
  }

  private AnalyticsPeriodResponse.CurrencyMetricsResponse toCurrencyResponse(
      AnalyticsPeriodVo.CurrencyMetrics source) {
    return new AnalyticsPeriodResponse.CurrencyMetricsResponse(
        source.currency(),
        toTotalsResponse(source.totals()),
        toDimensionResponses(source.categories()),
        toDimensionResponses(source.accounts()),
        toDimensionResponses(source.persons()),
        toDimensionResponses(source.merchants()));
  }

  private AnalyticsPeriodResponse.MetricTotalsResponse toTotalsResponse(
      AnalyticsPeriodVo.MetricTotals source) {
    return new AnalyticsPeriodResponse.MetricTotalsResponse(
        source.income(),
        source.expense(),
        source.netSurplus());
  }

  private List<AnalyticsPeriodResponse.DimensionMetricResponse> toDimensionResponses(
      List<AnalyticsPeriodVo.DimensionMetric> source) {
    return source.stream()
        .map(
            item ->
                new AnalyticsPeriodResponse.DimensionMetricResponse(
                    item.key(),
                    item.label(),
                    item.income(),
                    item.expense(),
                    item.netSurplus()))
        .toList();
  }
}
