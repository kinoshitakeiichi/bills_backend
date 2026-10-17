package com.jiajunxie.service.impl;

import com.jiajunxie.domain.analytics.AnalyticsComparisonContract;
import com.jiajunxie.domain.analytics.AnalyticsComparisonContract.MetricComparison;
import com.jiajunxie.domain.analytics.AnalyticsComparisonContract.PeriodRange;
import com.jiajunxie.service.AnalyticsComparisonService;
import com.jiajunxie.service.AnalyticsService;
import com.jiajunxie.vo.AnalyticsComparisonVo;
import com.jiajunxie.vo.AnalyticsPeriodVo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalyticsComparisonServiceImpl implements AnalyticsComparisonService {
  private static final AnalyticsComparisonContract CONTRACT =
      new AnalyticsComparisonContract();

  private final AnalyticsService analyticsService;

  @Override
  public AnalyticsComparisonVo getComparison(
      LocalDate periodStart,
      LocalDate periodEnd,
      ZoneId timezone) {
    PeriodRange currentRange = new PeriodRange(periodStart, periodEnd);
    PeriodRange previousRange = CONTRACT.previousPeriod(currentRange);
    PeriodRange yearOverYearRange = CONTRACT.yearOverYearPeriod(currentRange);

    AnalyticsPeriodVo current =
        analyticsService.getPeriod(currentRange.start(), currentRange.end(), timezone);
    AnalyticsPeriodVo previous =
        analyticsService.getPeriod(previousRange.start(), previousRange.end(), timezone);
    AnalyticsPeriodVo yearOverYear =
        analyticsService.getPeriod(
            yearOverYearRange.start(), yearOverYearRange.end(), timezone);

    Map<String, AnalyticsPeriodVo.MetricTotals> currentByCurrency = totalsByCurrency(current);
    Map<String, AnalyticsPeriodVo.MetricTotals> previousByCurrency = totalsByCurrency(previous);
    Map<String, AnalyticsPeriodVo.MetricTotals> yearOverYearByCurrency =
        totalsByCurrency(yearOverYear);

    Set<String> currencies = new LinkedHashSet<>();
    currencies.addAll(currentByCurrency.keySet());
    currencies.addAll(previousByCurrency.keySet());
    currencies.addAll(yearOverYearByCurrency.keySet());

    List<AnalyticsComparisonVo.CurrencyComparison> currencyComparisons =
        currencies.stream()
            .sorted()
            .map(
                currency ->
                    compareCurrency(
                        currency,
                        currentByCurrency.get(currency),
                        previousByCurrency.get(currency),
                        yearOverYearByCurrency.get(currency)))
            .toList();

    return new AnalyticsComparisonVo(
        toVoRange(currentRange),
        toVoRange(previousRange),
        toVoRange(yearOverYearRange),
        currencyComparisons);
  }

  private Map<String, AnalyticsPeriodVo.MetricTotals> totalsByCurrency(
      AnalyticsPeriodVo period) {
    return period.currencies().stream()
        .collect(
            Collectors.toMap(
                AnalyticsPeriodVo.CurrencyMetrics::currency,
                AnalyticsPeriodVo.CurrencyMetrics::totals));
  }

  private AnalyticsComparisonVo.CurrencyComparison compareCurrency(
      String currency,
      AnalyticsPeriodVo.MetricTotals current,
      AnalyticsPeriodVo.MetricTotals previous,
      AnalyticsPeriodVo.MetricTotals yearOverYear) {
    AnalyticsPeriodVo.MetricTotals currentTotals = totalsOrZero(current);
    AnalyticsPeriodVo.MetricTotals previousTotals = totalsOrZero(previous);
    AnalyticsPeriodVo.MetricTotals yearOverYearTotals = totalsOrZero(yearOverYear);

    return new AnalyticsComparisonVo.CurrencyComparison(
        currency,
        compareMetric(
            currentTotals.income(),
            previousTotals.income(),
            yearOverYearTotals.income()),
        compareMetric(
            currentTotals.expense(),
            previousTotals.expense(),
            yearOverYearTotals.expense()),
        compareMetric(
            currentTotals.netSurplus(),
            previousTotals.netSurplus(),
            yearOverYearTotals.netSurplus()));
  }

  private AnalyticsComparisonVo.MetricComparison compareMetric(
      BigDecimal current,
      BigDecimal previous,
      BigDecimal yearOverYear) {
    MetricComparison previousComparison = CONTRACT.compare(current, previous);
    MetricComparison yearOverYearComparison = CONTRACT.compare(current, yearOverYear);

    return new AnalyticsComparisonVo.MetricComparison(
        current,
        toBaseline(previousComparison),
        toBaseline(yearOverYearComparison));
  }

  private AnalyticsComparisonVo.BaselineComparison toBaseline(
      MetricComparison comparison) {
    return new AnalyticsComparisonVo.BaselineComparison(
        comparison.baseline(),
        comparison.delta(),
        comparison.changeRate());
  }

  private AnalyticsPeriodVo.MetricTotals totalsOrZero(
      AnalyticsPeriodVo.MetricTotals totals) {
    return totals == null
        ? new AnalyticsPeriodVo.MetricTotals(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO)
        : totals;
  }

  private AnalyticsComparisonVo.PeriodRange toVoRange(PeriodRange range) {
    return new AnalyticsComparisonVo.PeriodRange(range.start(), range.end());
  }
}
