package com.jiajunxie.convert;

import com.jiajunxie.response.AnalyticsComparisonResponse;
import com.jiajunxie.vo.AnalyticsComparisonVo;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsComparisonConvertWebMapper {

  public AnalyticsComparisonResponse toResponse(AnalyticsComparisonVo source) {
    return new AnalyticsComparisonResponse(
        toPeriodRange(source.currentPeriod()),
        toPeriodRange(source.previousPeriod()),
        toPeriodRange(source.yearOverYearPeriod()),
        source.currencies().stream().map(this::toCurrency).toList());
  }

  private AnalyticsComparisonResponse.PeriodRangeResponse toPeriodRange(
      AnalyticsComparisonVo.PeriodRange source) {
    return new AnalyticsComparisonResponse.PeriodRangeResponse(
        source.start(),
        source.end());
  }

  private AnalyticsComparisonResponse.CurrencyComparisonResponse toCurrency(
      AnalyticsComparisonVo.CurrencyComparison source) {
    return new AnalyticsComparisonResponse.CurrencyComparisonResponse(
        source.currency(),
        toMetric(source.income()),
        toMetric(source.expense()),
        toMetric(source.netSurplus()));
  }

  private AnalyticsComparisonResponse.MetricComparisonResponse toMetric(
      AnalyticsComparisonVo.MetricComparison source) {
    return new AnalyticsComparisonResponse.MetricComparisonResponse(
        source.current(),
        toBaseline(source.previousPeriod()),
        toBaseline(source.yearOverYear()));
  }

  private AnalyticsComparisonResponse.BaselineComparisonResponse toBaseline(
      AnalyticsComparisonVo.BaselineComparison source) {
    return new AnalyticsComparisonResponse.BaselineComparisonResponse(
        source.baseline(),
        source.delta(),
        source.changeRate());
  }
}
