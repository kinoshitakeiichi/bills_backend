package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestOperations;

class TradingViewRateProviderImplTest {

  @Test
  void returnsDirectTradingViewQuote() {
    RestOperations rest = org.mockito.Mockito.mock(RestOperations.class);
    when(rest.postForObject(any(String.class), any(), eq(Map.class)))
        .thenReturn(Map.of("data", List.of(Map.of("d", List.of(150.25)))));

    TradingViewRateProviderImpl provider = new TradingViewRateProviderImpl(rest);

    var result = provider.getQuote("usd", "jpy");

    assertEquals("USD", result.baseCurrency());
    assertEquals("JPY", result.quoteCurrency());
    assertEquals(new BigDecimal("150.25"), result.exchangeRate());
    assertEquals("TRADINGVIEW", result.rateSourceCode());
    assertTrue(!result.derived());
  }

  @Test
  void derivesInverseWhenDirectTickerHasNoData() {
    RestOperations rest = org.mockito.Mockito.mock(RestOperations.class);
    when(rest.postForObject(any(String.class), any(), eq(Map.class)))
        .thenReturn(
            Map.of("data", List.of()),
            Map.of("data", List.of(Map.of("d", List.of(0.0066666667)))));

    TradingViewRateProviderImpl provider = new TradingViewRateProviderImpl(rest);

    var result = provider.getQuote("USD", "JPY");

    assertEquals(new BigDecimal("149.9999992500"), result.exchangeRate());
    assertTrue(result.derived());
  }

  @Test
  void sameCurrencyReturnsOneWithoutRemoteCall() {
    RestOperations rest = org.mockito.Mockito.mock(RestOperations.class);
    TradingViewRateProviderImpl provider = new TradingViewRateProviderImpl(rest);

    var result = provider.getQuote("JPY", "JPY");

    assertEquals(BigDecimal.ONE, result.exchangeRate());
    assertTrue(result.derived());
  }
}
