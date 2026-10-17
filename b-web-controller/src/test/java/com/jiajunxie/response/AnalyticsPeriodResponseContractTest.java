package com.jiajunxie.response;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AnalyticsPeriodResponseContractTest {

  @Test
  void rootResponseHasNoCrossCurrencyGrandTotal() {
    Set<String> rootFields =
        Arrays.stream(AnalyticsPeriodResponse.class.getRecordComponents())
            .map(component -> component.getName())
            .collect(Collectors.toSet());

    assertTrue(rootFields.contains("periodStart"));
    assertTrue(rootFields.contains("periodEnd"));
    assertTrue(rootFields.contains("currencies"));

    assertTrue(!rootFields.contains("income"));
    assertTrue(!rootFields.contains("expense"));
    assertTrue(!rootFields.contains("netSurplus"));
    assertTrue(!rootFields.contains("total"));
  }

  @Test
  void everyCurrencyOwnsItsTotalsAndReadableBreakdowns() {
    Set<String> currencyFields =
        Arrays.stream(
                AnalyticsPeriodResponse.CurrencyMetricsResponse.class.getRecordComponents())
            .map(component -> component.getName())
            .collect(Collectors.toSet());

    assertTrue(currencyFields.contains("currency"));
    assertTrue(currencyFields.contains("totals"));
    assertTrue(currencyFields.contains("categories"));
    assertTrue(currencyFields.contains("accounts"));
    assertTrue(currencyFields.contains("persons"));
    assertTrue(currencyFields.contains("merchants"));

    Set<String> dimensionFields =
        Arrays.stream(
                AnalyticsPeriodResponse.DimensionMetricResponse.class.getRecordComponents())
            .map(component -> component.getName())
            .collect(Collectors.toSet());

    assertTrue(dimensionFields.contains("key"));
    assertTrue(dimensionFields.contains("label"));
    assertTrue(dimensionFields.contains("income"));
    assertTrue(dimensionFields.contains("expense"));
    assertTrue(dimensionFields.contains("netSurplus"));
  }
}
