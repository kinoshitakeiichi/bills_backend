package com.jiajunxie.domain.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AnalyticsComparisonContractTest {
  private final AnalyticsComparisonContract contract =
      new AnalyticsComparisonContract();

  @Test
  void previousMonthUsesImmediatelyPrecedingEqualLengthPeriod() {
    var current =
        new AnalyticsComparisonContract.PeriodRange(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31));

    assertEquals(
        new AnalyticsComparisonContract.PeriodRange(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31)),
        contract.previousPeriod(current));
  }

  @Test
  void arbitraryCustomRangeUsesSameNumberOfDaysForPreviousPeriod() {
    var current =
        new AnalyticsComparisonContract.PeriodRange(
            LocalDate.of(2026, 8, 11),
            LocalDate.of(2026, 8, 20));

    assertEquals(
        new AnalyticsComparisonContract.PeriodRange(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 10)),
        contract.previousPeriod(current));
  }

  @Test
  void yearOverYearMovesBothBoundariesBackOneYear() {
    var current =
        new AnalyticsComparisonContract.PeriodRange(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31));

    assertEquals(
        new AnalyticsComparisonContract.PeriodRange(
            LocalDate.of(2025, 8, 1),
            LocalDate.of(2025, 8, 31)),
        contract.yearOverYearPeriod(current));
  }

  @Test
  void yearOverYearUsesLocalDateLeapYearSemantics() {
    var current =
        new AnalyticsComparisonContract.PeriodRange(
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2024, 2, 29));

    assertEquals(
        new AnalyticsComparisonContract.PeriodRange(
            LocalDate.of(2023, 2, 28),
            LocalDate.of(2023, 2, 28)),
        contract.yearOverYearPeriod(current));
  }

  @Test
  void comparisonReturnsDeltaAndRatio() {
    var result =
        contract.compare(
            new BigDecimal("125"),
            new BigDecimal("100"));

    assertEquals(new BigDecimal("25"), result.delta());
    assertEquals(new BigDecimal("0.25"), result.changeRate());
  }

  @Test
  void decreaseUsesNegativeDeltaAndRate() {
    var result =
        contract.compare(
            new BigDecimal("75"),
            new BigDecimal("100"));

    assertEquals(new BigDecimal("-25"), result.delta());
    assertEquals(new BigDecimal("-0.25"), result.changeRate());
  }

  @Test
  void zeroBaselineKeepsDeltaButRateIsUndefined() {
    var result =
        contract.compare(
            new BigDecimal("100"),
            BigDecimal.ZERO);

    assertEquals(new BigDecimal("100"), result.delta());
    assertNull(result.changeRate());
  }

  @Test
  void nullMetricAmountsNormalizeToZero() {
    var result = contract.compare(null, null);

    assertEquals(BigDecimal.ZERO, result.current());
    assertEquals(BigDecimal.ZERO, result.baseline());
    assertEquals(BigDecimal.ZERO, result.delta());
    assertNull(result.changeRate());
  }

  @Test
  void invalidPeriodFailsFast() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AnalyticsComparisonContract.PeriodRange(
                LocalDate.of(2026, 8, 31),
                LocalDate.of(2026, 8, 1)));
  }
}
