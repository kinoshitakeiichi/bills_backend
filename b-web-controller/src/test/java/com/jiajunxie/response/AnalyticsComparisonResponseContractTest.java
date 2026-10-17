package com.jiajunxie.response;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AnalyticsComparisonResponseContractTest {

  @Test
  void rootResponseOnlyCarriesPeriodsAndPerCurrencyComparisons() {
    Set<String> fields =
        Arrays.stream(AnalyticsComparisonResponse.class.getRecordComponents())
            .map(component -> component.getName())
            .collect(Collectors.toSet());

    assertTrue(fields.contains("currentPeriod"));
    assertTrue(fields.contains("previousPeriod"));
    assertTrue(fields.contains("yearOverYearPeriod"));
    assertTrue(fields.contains("currencies"));

    assertTrue(!fields.contains("income"));
    assertTrue(!fields.contains("expense"));
    assertTrue(!fields.contains("netSurplus"));
    assertTrue(!fields.contains("changeRate"));
  }

  @Test
  void currencyComparisonContainsThreeMetricsAndEachMetricContainsBothBaselines() {
    Set<String> currencyFields =
        Arrays.stream(
                AnalyticsComparisonResponse.CurrencyComparisonResponse.class.getRecordComponents())
            .map(component -> component.getName())
            .collect(Collectors.toSet());

    assertTrue(currencyFields.contains("currency"));
    assertTrue(currencyFields.contains("income"));
    assertTrue(currencyFields.contains("expense"));
    assertTrue(currencyFields.contains("netSurplus"));

    Set<String> metricFields =
        Arrays.stream(
                AnalyticsComparisonResponse.MetricComparisonResponse.class.getRecordComponents())
            .map(component -> component.getName())
            .collect(Collectors.toSet());

    assertTrue(metricFields.contains("current"));
    assertTrue(metricFields.contains("previousPeriod"));
    assertTrue(metricFields.contains("yearOverYear"));
  }
}
