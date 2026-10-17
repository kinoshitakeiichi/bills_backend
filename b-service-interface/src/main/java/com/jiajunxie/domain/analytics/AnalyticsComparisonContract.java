package com.jiajunxie.domain.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public final class AnalyticsComparisonContract {

  public record PeriodRange(LocalDate start, LocalDate end) {
    public PeriodRange {
      if (start == null || end == null || end.isBefore(start)) {
        throw new IllegalArgumentException("comparison period is invalid");
      }
    }
  }

  public record MetricComparison(
      BigDecimal current,
      BigDecimal baseline,
      BigDecimal delta,
      BigDecimal changeRate) {}

  /**
   * 环比：当前周期与紧邻的、长度完全相同的上一周期比较。
   *
   * <p>例如 2026-08-01..2026-08-31 -> 2026-07-01..2026-07-31。
   * 自定义 10 天周期则比较紧邻的前 10 天。
   */
  public PeriodRange previousPeriod(PeriodRange current) {
    long days = current.end().toEpochDay() - current.start().toEpochDay() + 1;
    LocalDate previousEnd = current.start().minusDays(1);
    LocalDate previousStart = previousEnd.minusDays(days - 1);
    return new PeriodRange(previousStart, previousEnd);
  }

  /**
   * 同比：当前起止日期分别向前移动一年。
   *
   * <p>LocalDate 自身负责闰年边界，例如 2024-02-29 minusYears(1) -> 2023-02-28。
   */
  public PeriodRange yearOverYearPeriod(PeriodRange current) {
    return new PeriodRange(
        current.start().minusYears(1),
        current.end().minusYears(1));
  }

  /**
   * changeRate 为比例而非百分数：0.25 表示 +25%。
   *
   * <p>baseline=0 时比例没有数学定义，因此返回 null；delta 仍然保留。
   */
  public MetricComparison compare(BigDecimal current, BigDecimal baseline) {
    BigDecimal normalizedCurrent = normalize(current);
    BigDecimal normalizedBaseline = normalize(baseline);
    BigDecimal delta = normalizedCurrent.subtract(normalizedBaseline);

    BigDecimal changeRate =
        normalizedBaseline.signum() == 0
            ? null
            : delta
                .divide(normalizedBaseline.abs(), 8, RoundingMode.HALF_UP)
                .stripTrailingZeros();

    return new MetricComparison(
        normalizedCurrent,
        normalizedBaseline,
        delta,
        changeRate);
  }

  private BigDecimal normalize(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
