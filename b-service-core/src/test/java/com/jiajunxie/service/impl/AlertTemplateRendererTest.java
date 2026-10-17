package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlertTemplateRendererTest {

  private final AlertTemplateRenderer renderer = new AlertTemplateRenderer();

  @Test
  void supportsVariablesArithmeticFunctionsAndExplicitNewline() {
    Map<String, String> text =
        Map.of("baseCurrency", "USD", "quoteCurrency", "JPY", "rateSource", "TRADINGVIEW");
    Map<String, BigDecimal> numbers = new HashMap<>();
    numbers.put("currentRate", new BigDecimal("155.20"));
    numbers.put("previousRate", new BigDecimal("154.80"));
    numbers.put("thresholdRate", new BigDecimal("158"));

    String rendered =
        renderer.render(
            "当前汇率：{currentRate}\\n"
                + "差额：{round(currentRate - previousRate, 4)}\\n"
                + "变化率：{round((currentRate - previousRate) / previousRate * 100, 2)}%",
            text,
            numbers);

    assertEquals("当前汇率：155.2\n差额：0.4000\n变化率：0.26%", rendered);
  }

  @Test
  void missingPreviousRateAndInvalidExpressionRenderDashWithoutThrowing() {
    Map<String, BigDecimal> numbers = new HashMap<>();
    numbers.put("currentRate", new BigDecimal("155.2"));
    numbers.put("previousRate", null);

    assertEquals(
        "差额：-\n非法：-",
        renderer.render(
            "差额：{currentRate - previousRate}\n非法：{currentRate / 0}",
            Map.of(),
            numbers));
  }

  @Test
  void doesNotAllowMethodOrClassAccess() {
    assertEquals(
        "value=-",
        renderer.render(
            "value={currentRate.toString()}",
            Map.of(),
            Map.of("currentRate", BigDecimal.ONE)));
  }

  @Test
  void supportsAbsAndParentheses() {
    assertEquals(
        "0.4",
        renderer.render(
            "{abs((previousRate - currentRate))}",
            Map.of(),
            Map.of(
                "currentRate", new BigDecimal("155.2"),
                "previousRate", new BigDecimal("154.8"))));
  }
}
