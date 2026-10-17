package com.jiajunxie.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.convert.AnalyticsConvertWebMapper;
import com.jiajunxie.service.AnalyticsService;
import com.jiajunxie.vo.AnalyticsPeriodVo;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class AnalyticsControllerContractTest {

  @Test
  void controllerUsesStableV1AnalyticsBasePath() {
    RequestMapping mapping = AnalyticsController.class.getAnnotation(RequestMapping.class);
    assertNotNull(mapping);
    assertEquals("/api/v1/analytics", mapping.value()[0]);
  }

  @Test
  void periodEndpointIsExplicitAndTimezoneAware() throws Exception {
    Method method =
        AnalyticsController.class.getMethod(
            "period", LocalDate.class, LocalDate.class, String.class);
    GetMapping mapping = method.getAnnotation(GetMapping.class);
    assertNotNull(mapping);
    assertEquals("/period", mapping.value()[0]);

    RecordingAnalyticsService service = new RecordingAnalyticsService();
    AnalyticsController controller =
        new AnalyticsController(service, new AnalyticsConvertWebMapper());

    var response =
        controller.period(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            "Asia/Tokyo");

    assertEquals(LocalDate.of(2026, 8, 1), service.start);
    assertEquals(LocalDate.of(2026, 8, 31), service.end);
    assertEquals(ZoneId.of("Asia/Tokyo"), service.timezone);
    assertEquals("JPY", response.getBody().data().currencies().get(0).currency());
  }

  @Test
  void invalidTimezoneFailsBeforeCallingService() {
    RecordingAnalyticsService service = new RecordingAnalyticsService();
    AnalyticsController controller =
        new AnalyticsController(service, new AnalyticsConvertWebMapper());

    assertThrows(
        BusinessException.class,
        () ->
            controller.period(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                "Mars/Olympus"));

    assertEquals(null, service.start);
  }

  @Test
  void converterPreservesReadableDimensionLabelsAndCurrencySeparation() {
    AnalyticsConvertWebMapper mapper = new AnalyticsConvertWebMapper();

    AnalyticsPeriodVo source =
        new AnalyticsPeriodVo(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            List.of(
                currency("JPY", "餐饮 / 咖啡", new BigDecimal("100")),
                currency("USD", "Travel", new BigDecimal("20"))));

    var response = mapper.toResponse(source);

    assertEquals(2, response.currencies().size());
    assertEquals("JPY", response.currencies().get(0).currency());
    assertEquals("餐饮 / 咖啡", response.currencies().get(0).categories().get(0).label());
    assertEquals("USD", response.currencies().get(1).currency());
  }

  private AnalyticsPeriodVo.CurrencyMetrics currency(
      String currency,
      String categoryLabel,
      BigDecimal expense) {
    AnalyticsPeriodVo.MetricTotals totals =
        new AnalyticsPeriodVo.MetricTotals(
            BigDecimal.ZERO,
            expense,
            expense.negate());
    AnalyticsPeriodVo.DimensionMetric category =
        new AnalyticsPeriodVo.DimensionMetric(
            "category",
            categoryLabel,
            BigDecimal.ZERO,
            expense,
            expense.negate());
    return new AnalyticsPeriodVo.CurrencyMetrics(
        currency,
        totals,
        List.of(category),
        List.of(),
        List.of(),
        List.of());
  }

  private static class RecordingAnalyticsService implements AnalyticsService {
    private LocalDate start;
    private LocalDate end;
    private ZoneId timezone;

    @Override
    public AnalyticsPeriodVo getPeriod(
        LocalDate periodStart,
        LocalDate periodEnd,
        ZoneId timezone) {
      this.start = periodStart;
      this.end = periodEnd;
      this.timezone = timezone;

      return new AnalyticsPeriodVo(
          periodStart,
          periodEnd,
          List.of(
              new AnalyticsPeriodVo.CurrencyMetrics(
                  "JPY",
                  new AnalyticsPeriodVo.MetricTotals(
                      new BigDecimal("300"),
                      new BigDecimal("70"),
                      new BigDecimal("230")),
                  List.of(),
                  List.of(),
                  List.of(),
                  List.of())));
    }
  }
}
