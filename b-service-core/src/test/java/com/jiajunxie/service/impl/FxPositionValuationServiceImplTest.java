package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.service.ExchangeRateService;
import com.jiajunxie.service.FxExchangeService;
import com.jiajunxie.vo.ExchangeRateVo;
import com.jiajunxie.vo.FxPositionVo;
import com.jiajunxie.vo.RealtimeExchangeRateVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FxPositionValuationServiceImplTest {

  @Test
  void realtimeCalculatesMarketValueAndUnrealizedGain() {
    FxExchangeService fxService = org.mockito.Mockito.mock(FxExchangeService.class);
    ExchangeRateService rateService = org.mockito.Mockito.mock(ExchangeRateService.class);
    FxPositionValuationServiceImpl service =
        new FxPositionValuationServiceImpl(fxService, rateService);

    when(fxService.getPosition("usd"))
        .thenReturn(position("usd", "USD", "JPY", "2000", "290000", "145"));
    Instant observedAt = Instant.parse("2026-08-10T09:00:00Z");
    when(rateService.realtime("USD", "JPY"))
        .thenReturn(
            new RealtimeExchangeRateVo(
                "USD", "JPY", new BigDecimal("150"), "TRADINGVIEW", observedAt, false));

    var result = service.realtime("usd");

    assertEquals("REALTIME", result.valuationMode());
    assertEquals(new BigDecimal("300000.0000"), result.marketValue());
    assertEquals(new BigDecimal("10000.0000"), result.unrealizedGainLoss());
    assertEquals(new BigDecimal("0.0344827586"), result.unrealizedReturnRate());
    assertEquals("TRADINGVIEW", result.rateSourceCode());
    assertEquals(observedAt, result.observedAt());
  }

  @Test
  void referenceUsesActualEffectiveRateDateAndSource() {
    FxExchangeService fxService = org.mockito.Mockito.mock(FxExchangeService.class);
    ExchangeRateService rateService = org.mockito.Mockito.mock(ExchangeRateService.class);
    FxPositionValuationServiceImpl service =
        new FxPositionValuationServiceImpl(fxService, rateService);

    when(fxService.getPosition("usd"))
        .thenReturn(position("usd", "USD", "JPY", "2000", "290000", "145"));
    when(rateService.effective(LocalDate.of(2026, 8, 10), "USD", "JPY", null))
        .thenReturn(
            new ExchangeRateVo(
                "r1",
                LocalDate.of(2026, 8, 8),
                "USD",
                "JPY",
                new BigDecimal("140"),
                "MANUAL",
                Instant.parse("2026-08-08T00:00:00Z"),
                false));

    var result = service.reference("usd", LocalDate.of(2026, 8, 10), null);

    assertEquals("STORED", result.valuationMode());
    assertEquals(LocalDate.of(2026, 8, 10), result.valuationDate());
    assertEquals(LocalDate.of(2026, 8, 8), result.rateDate());
    assertEquals("MANUAL", result.rateSourceCode());
    assertEquals(new BigDecimal("280000.0000"), result.marketValue());
    assertEquals(new BigDecimal("-10000.0000"), result.unrealizedGainLoss());
    assertEquals(new BigDecimal("-0.0344827586"), result.unrealizedReturnRate());
  }

  @Test
  void zeroCostPositionReturnsZeroReturnRate() {
    FxExchangeService fxService = org.mockito.Mockito.mock(FxExchangeService.class);
    ExchangeRateService rateService = org.mockito.Mockito.mock(ExchangeRateService.class);
    FxPositionValuationServiceImpl service =
        new FxPositionValuationServiceImpl(fxService, rateService);

    when(fxService.getPosition("usd"))
        .thenReturn(position("usd", "USD", "JPY", "0", "0", "0"));
    when(rateService.realtime("USD", "JPY"))
        .thenReturn(
            new RealtimeExchangeRateVo(
                "USD", "JPY", new BigDecimal("150"), "TRADINGVIEW", Instant.now(), false));

    var result = service.realtime("usd");

    assertEquals(new BigDecimal("0.0000000000"), result.unrealizedReturnRate());
  }

  @Test
  void missingPositionIsRejectedBeforeRateLookup() {
    FxExchangeService fxService = org.mockito.Mockito.mock(FxExchangeService.class);
    ExchangeRateService rateService = org.mockito.Mockito.mock(ExchangeRateService.class);
    FxPositionValuationServiceImpl service =
        new FxPositionValuationServiceImpl(fxService, rateService);

    when(fxService.getPosition("empty")).thenReturn(null);

    assertThrows(BusinessException.class, () -> service.realtime("empty"));

    verify(fxService).getPosition("empty");
    org.mockito.Mockito.verifyNoInteractions(rateService);
  }

  private FxPositionVo position(
      String accountId,
      String currency,
      String costCurrency,
      String quantity,
      String totalCost,
      String averageCostRate) {
    return new FxPositionVo(
        "p1",
        accountId,
        currency,
        new BigDecimal(quantity),
        new BigDecimal(totalCost),
        costCurrency,
        new BigDecimal(averageCostRate),
        Instant.parse("2026-08-10T00:00:00Z"));
  }
}
