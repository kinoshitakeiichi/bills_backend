package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiajunxie.service.AnalyticsService;
import com.jiajunxie.vo.AnalyticsPeriodVo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsComparisonServiceImplTest {

  @Test
  void comparesCurrentPreviousAndYearOverYearForEachCurrency() {
    AnalyticsService analyticsService = mock(AnalyticsService.class);
    AnalyticsComparisonServiceImpl service =
        new AnalyticsComparisonServiceImpl(analyticsService);
    ZoneId zone = ZoneId.of("Asia/Tokyo");

    when(analyticsService.getPeriod(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            zone))
        .thenReturn(
            period(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                currency("JPY", "300", "100", "200"),
                currency("USD", "0", "20", "-20")));

    when(analyticsService.getPeriod(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            zone))
        .thenReturn(
            period(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                currency("JPY", "200", "80", "120")));

    when(analyticsService.getPeriod(
            LocalDate.of(2025, 8, 1),
            LocalDate.of(2025, 8, 31),
            zone))
        .thenReturn(
            period(
                LocalDate.of(2025, 8, 1),
                LocalDate.of(2025, 8, 31),
                currency("JPY", "150", "125", "25"),
                currency("USD", "0", "10", "-10")));

    var result =
        service.getComparison(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            zone);

    assertEquals(
        new com.jiajunxie.vo.AnalyticsComparisonVo.PeriodRange(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31)),
        result.previousPeriod());
    assertEquals(
        new com.jiajunxie.vo.AnalyticsComparisonVo.PeriodRange(
            LocalDate.of(2025, 8, 1),
            LocalDate.of(2025, 8, 31)),
        result.yearOverYearPeriod());

    assertEquals(List.of("JPY", "USD"), result.currencies().stream().map(v -> v.currency()).toList());

    var jpy = result.currencies().get(0);
    assertEquals(new BigDecimal("300"), jpy.income().current());
    assertEquals(new BigDecimal("200"), jpy.income().previousPeriod().baseline());
    assertEquals(new BigDecimal("100"), jpy.income().previousPeriod().delta());
    assertEquals(new BigDecimal("0.5"), jpy.income().previousPeriod().changeRate());
    assertEquals(new BigDecimal("150"), jpy.income().yearOverYear().baseline());
    assertEquals(new BigDecimal("1"), jpy.income().yearOverYear().changeRate());

    assertEquals(new BigDecimal("100"), jpy.expense().current());
    assertEquals(new BigDecimal("80"), jpy.expense().previousPeriod().baseline());
    assertEquals(new BigDecimal("0.25"), jpy.expense().previousPeriod().changeRate());

    var usd = result.currencies().get(1);
    assertEquals(BigDecimal.ZERO, usd.expense().previousPeriod().baseline());
    assertEquals(new BigDecimal("20"), usd.expense().previousPeriod().delta());
    assertNull(usd.expense().previousPeriod().changeRate());
    assertEquals(new BigDecimal("10"), usd.expense().yearOverYear().baseline());
    assertEquals(new BigDecimal("1"), usd.expense().yearOverYear().changeRate());
  }

  @Test
  void currencyPresentOnlyInBaselineIsStillReturned() {
    AnalyticsService analyticsService = mock(AnalyticsService.class);
    AnalyticsComparisonServiceImpl service =
        new AnalyticsComparisonServiceImpl(analyticsService);
    ZoneId zone = ZoneId.of("Asia/Tokyo");

    when(analyticsService.getPeriod(
            LocalDate.of(2026, 8, 11),
            LocalDate.of(2026, 8, 20),
            zone))
        .thenReturn(period(LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 20)));

    when(analyticsService.getPeriod(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 10),
            zone))
        .thenReturn(
            period(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10),
                currency("EUR", "50", "0", "50")));

    when(analyticsService.getPeriod(
            LocalDate.of(2025, 8, 11),
            LocalDate.of(2025, 8, 20),
            zone))
        .thenReturn(period(LocalDate.of(2025, 8, 11), LocalDate.of(2025, 8, 20)));

    var result =
        service.getComparison(
            LocalDate.of(2026, 8, 11),
            LocalDate.of(2026, 8, 20),
            zone);

    assertEquals(1, result.currencies().size());
    var eur = result.currencies().get(0);
    assertEquals("EUR", eur.currency());
    assertEquals(BigDecimal.ZERO, eur.income().current());
    assertEquals(new BigDecimal("50"), eur.income().previousPeriod().baseline());
    assertEquals(new BigDecimal("-50"), eur.income().previousPeriod().delta());
    assertEquals(new BigDecimal("-1"), eur.income().previousPeriod().changeRate());
  }

  @Test
  void comparisonDelegatesAllThreeQueriesWithTheSameTimezone() {
    AnalyticsService analyticsService = mock(AnalyticsService.class);
    AnalyticsComparisonServiceImpl service =
        new AnalyticsComparisonServiceImpl(analyticsService);
    ZoneId zone = ZoneId.of("Asia/Tokyo");

    when(analyticsService.getPeriod(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(zone)))
        .thenReturn(period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)));

    service.getComparison(
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 31),
        zone);

    verify(analyticsService)
        .getPeriod(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            zone);
    verify(analyticsService)
        .getPeriod(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            zone);
    verify(analyticsService)
        .getPeriod(
            LocalDate.of(2025, 8, 1),
            LocalDate.of(2025, 8, 31),
            zone);
  }

  private AnalyticsPeriodVo period(
      LocalDate start,
      LocalDate end,
      AnalyticsPeriodVo.CurrencyMetrics... currencies) {
    return new AnalyticsPeriodVo(start, end, List.of(currencies));
  }

  private AnalyticsPeriodVo.CurrencyMetrics currency(
      String currency,
      String income,
      String expense,
      String netSurplus) {
    return new AnalyticsPeriodVo.CurrencyMetrics(
        currency,
        new AnalyticsPeriodVo.MetricTotals(
            new BigDecimal(income),
            new BigDecimal(expense),
            new BigDecimal(netSurplus)),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }
}
