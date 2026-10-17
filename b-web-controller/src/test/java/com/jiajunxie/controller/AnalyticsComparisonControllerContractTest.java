package com.jiajunxie.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.convert.AnalyticsComparisonConvertWebMapper;
import com.jiajunxie.service.AnalyticsComparisonService;
import com.jiajunxie.vo.AnalyticsComparisonVo;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class AnalyticsComparisonControllerContractTest {

  @Test
  void controllerUsesStableAnalyticsBasePathAndExplicitComparisonEndpoint() throws Exception {
    RequestMapping mapping =
        AnalyticsComparisonController.class.getAnnotation(RequestMapping.class);
    assertNotNull(mapping);
    assertEquals("/api/v1/analytics", mapping.value()[0]);

    Method method =
        AnalyticsComparisonController.class.getMethod(
            "comparison", LocalDate.class, LocalDate.class, String.class);
    GetMapping getMapping = method.getAnnotation(GetMapping.class);
    assertNotNull(getMapping);
    assertEquals("/comparison", getMapping.value()[0]);
  }

  @Test
  void comparisonEndpointPassesDatesAndTimezoneToService() {
    RecordingComparisonService service = new RecordingComparisonService();
    AnalyticsComparisonController controller =
        new AnalyticsComparisonController(
            service,
            new AnalyticsComparisonConvertWebMapper());

    var response =
        controller.comparison(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            "Asia/Tokyo");

    assertEquals(LocalDate.of(2026, 8, 1), service.start);
    assertEquals(LocalDate.of(2026, 8, 31), service.end);
    assertEquals(ZoneId.of("Asia/Tokyo"), service.timezone);
    assertEquals("JPY", response.getBody().data().currencies().get(0).currency());
  }

  @Test
  void invalidTimezoneFailsBeforeServiceCall() {
    RecordingComparisonService service = new RecordingComparisonService();
    AnalyticsComparisonController controller =
        new AnalyticsComparisonController(
            service,
            new AnalyticsComparisonConvertWebMapper());

    assertThrows(
        BusinessException.class,
        () ->
            controller.comparison(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                "Mars/Olympus"));

    assertNull(service.start);
  }

  @Test
  void converterPreservesNullChangeRateForZeroBaseline() {
    AnalyticsComparisonConvertWebMapper mapper =
        new AnalyticsComparisonConvertWebMapper();

    var response =
        mapper.toResponse(
            new AnalyticsComparisonVo(
                new AnalyticsComparisonVo.PeriodRange(
                    LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 8, 31)),
                new AnalyticsComparisonVo.PeriodRange(
                    LocalDate.of(2026, 7, 1),
                    LocalDate.of(2026, 7, 31)),
                new AnalyticsComparisonVo.PeriodRange(
                    LocalDate.of(2025, 8, 1),
                    LocalDate.of(2025, 8, 31)),
                List.of(
                    new AnalyticsComparisonVo.CurrencyComparison(
                        "JPY",
                        metric(
                            new BigDecimal("100"),
                            BigDecimal.ZERO,
                            new BigDecimal("100"),
                            null,
                            new BigDecimal("50"),
                            new BigDecimal("50"),
                            BigDecimal.ONE),
                        metric(
                            new BigDecimal("80"),
                            new BigDecimal("40"),
                            new BigDecimal("40"),
                            BigDecimal.ONE,
                            new BigDecimal("100"),
                            new BigDecimal("-20"),
                            new BigDecimal("-0.2")),
                        metric(
                            new BigDecimal("20"),
                            new BigDecimal("-40"),
                            new BigDecimal("60"),
                            new BigDecimal("1.5"),
                            new BigDecimal("-50"),
                            new BigDecimal("70"),
                            new BigDecimal("1.4"))))));

    var income = response.currencies().get(0).income();
    assertEquals(new BigDecimal("100"), income.current());
    assertEquals(BigDecimal.ZERO, income.previousPeriod().baseline());
    assertEquals(new BigDecimal("100"), income.previousPeriod().delta());
    assertNull(income.previousPeriod().changeRate());
    assertEquals(BigDecimal.ONE, income.yearOverYear().changeRate());
  }

  private static AnalyticsComparisonVo.MetricComparison metric(
      BigDecimal current,
      BigDecimal previousBaseline,
      BigDecimal previousDelta,
      BigDecimal previousRate,
      BigDecimal yoyBaseline,
      BigDecimal yoyDelta,
      BigDecimal yoyRate) {
    return new AnalyticsComparisonVo.MetricComparison(
        current,
        new AnalyticsComparisonVo.BaselineComparison(
            previousBaseline,
            previousDelta,
            previousRate),
        new AnalyticsComparisonVo.BaselineComparison(
            yoyBaseline,
            yoyDelta,
            yoyRate));
  }

  private static class RecordingComparisonService
      implements AnalyticsComparisonService {
    private LocalDate start;
    private LocalDate end;
    private ZoneId timezone;

    @Override
    public AnalyticsComparisonVo getComparison(
        LocalDate periodStart,
        LocalDate periodEnd,
        ZoneId timezone) {
      this.start = periodStart;
      this.end = periodEnd;
      this.timezone = timezone;
      return new AnalyticsComparisonVo(
          new AnalyticsComparisonVo.PeriodRange(periodStart, periodEnd),
          new AnalyticsComparisonVo.PeriodRange(
              periodStart.minusMonths(1),
              periodEnd.minusMonths(1)),
          new AnalyticsComparisonVo.PeriodRange(
              periodStart.minusYears(1),
              periodEnd.minusYears(1)),
          List.of(
              new AnalyticsComparisonVo.CurrencyComparison(
                  "JPY",
                  metric(
                      new BigDecimal("300"),
                      new BigDecimal("200"),
                      new BigDecimal("100"),
                      new BigDecimal("0.5"),
                      new BigDecimal("150"),
                      new BigDecimal("150"),
                      BigDecimal.ONE),
                  metric(
                      new BigDecimal("100"),
                      new BigDecimal("80"),
                      new BigDecimal("20"),
                      new BigDecimal("0.25"),
                      new BigDecimal("125"),
                      new BigDecimal("-25"),
                      new BigDecimal("-0.2")),
                  metric(
                      new BigDecimal("200"),
                      new BigDecimal("120"),
                      new BigDecimal("80"),
                      new BigDecimal("0.66666667"),
                      new BigDecimal("25"),
                      new BigDecimal("175"),
                      new BigDecimal("7")))));
    }
  }
}
