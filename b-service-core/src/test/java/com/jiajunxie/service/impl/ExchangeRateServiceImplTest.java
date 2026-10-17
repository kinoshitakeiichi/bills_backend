package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.ExchangeRate;
import com.jiajunxie.mapper.ExchangeRateMapper;
import com.jiajunxie.service.DictionaryService;
import com.jiajunxie.service.TradingViewRateProvider;
import com.jiajunxie.vo.DictionaryOptionVo;
import com.jiajunxie.vo.RealtimeExchangeRateVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceImplTest {

  @Mock ExchangeRateMapper exchangeRateMapper;
  @Mock DictionaryService dictionaryService;
  @Mock TradingViewRateProvider tradingViewRateProvider;

  private ExchangeRateServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new ExchangeRateServiceImpl(
            exchangeRateMapper, dictionaryService, tradingViewRateProvider);
    org.mockito.Mockito.lenient().when(dictionaryService.listOptions(eq("RATE_SOURCE"), anyString()))
        .thenAnswer(
            invocation -> {
              String code = invocation.getArgument(1);
              return List.of(new DictionaryOptionVo(code, code));
            });
  }

  @Test
  void manualUpsertCreatesNormalizedManualRate() {
    when(exchangeRateMapper.selectOne(any())).thenReturn(null);
    when(exchangeRateMapper.insert(any(ExchangeRate.class))).thenReturn(1);

    var result =
        service.upsertManual(
            LocalDate.of(2026, 8, 10), " usd ", "jpy", new BigDecimal("150.25"));

    assertEquals("USD", result.baseCurrency());
    assertEquals("JPY", result.quoteCurrency());
    assertEquals("MANUAL", result.rateSourceCode());
    assertEquals(new BigDecimal("150.25"), result.exchangeRate());

    verify(exchangeRateMapper).insert(any(ExchangeRate.class));
  }

  @Test
  void exactReturnsDirectStoredRate() {
    ExchangeRate direct = rate("USD", "JPY", "150.10", "MANUAL", LocalDate.of(2026, 8, 9));
    when(exchangeRateMapper.selectOne(any())).thenReturn(direct);

    var result =
        service.exact(LocalDate.of(2026, 8, 9), "USD", "JPY", "MANUAL");

    assertEquals(new BigDecimal("150.10"), result.exchangeRate());
    assertTrue(!result.derived());
  }

  @Test
  void exactDerivesInverseWhenDirectIsMissing() {
    ExchangeRate inverse = rate("JPY", "USD", "0.0066666667", "MANUAL", LocalDate.of(2026, 8, 9));
    when(exchangeRateMapper.selectOne(any())).thenReturn(null, inverse);

    var result =
        service.exact(LocalDate.of(2026, 8, 9), "USD", "JPY", "MANUAL");

    assertEquals(new BigDecimal("149.9999992500"), result.exchangeRate());
    assertTrue(result.derived());
    assertEquals("USD", result.baseCurrency());
    assertEquals("JPY", result.quoteCurrency());
  }

  @Test
  void effectiveReturnsLatestDirectRateOnOrBeforeValuationDate() {
    ExchangeRate direct = rate("USD", "JPY", "149.80", "TRADINGVIEW", LocalDate.of(2026, 8, 8));
    when(exchangeRateMapper.selectOne(any())).thenReturn(direct);

    var result =
        service.effective(LocalDate.of(2026, 8, 10), "USD", "JPY", null);

    assertEquals(LocalDate.of(2026, 8, 8), result.rateDate());
    assertEquals("TRADINGVIEW", result.rateSourceCode());
  }


  @Test
  void effectiveFallsBackToManualWhenTradingViewHasNoStoredRate() {
    ExchangeRate manual =
        rate("USD", "JPY", "148.60", "MANUAL", LocalDate.of(2026, 8, 7));
    when(exchangeRateMapper.selectOne(any())).thenReturn(null, null, manual);

    var result =
        service.effective(LocalDate.of(2026, 8, 10), "USD", "JPY", null);

    assertEquals(new BigDecimal("148.60"), result.exchangeRate());
    assertEquals("MANUAL", result.rateSourceCode());
  }

  @Test
  void sameCurrencyReturnsIdentityWithoutDatabaseLookup() {
    var result =
        service.effective(LocalDate.of(2026, 8, 10), "JPY", "JPY", "MANUAL");

    assertEquals(BigDecimal.ONE, result.exchangeRate());
    assertTrue(result.derived());
  }

  @Test
  void tradingViewSnapshotPersistsProviderQuoteAsTradingViewSource() {
    when(tradingViewRateProvider.getQuote("USD", "JPY"))
        .thenReturn(
            new RealtimeExchangeRateVo(
                "USD", "JPY", new BigDecimal("151.2345"), "TRADINGVIEW", Instant.now(), false));
    when(exchangeRateMapper.selectOne(any())).thenReturn(null);
    when(exchangeRateMapper.insert(any(ExchangeRate.class))).thenReturn(1);

    var result =
        service.snapshotTradingView(LocalDate.of(2026, 8, 10), "USD", "JPY");

    assertEquals("TRADINGVIEW", result.rateSourceCode());
    assertEquals(new BigDecimal("151.2345"), result.exchangeRate());
    verify(exchangeRateMapper).insert(any(ExchangeRate.class));
  }

  @Test
  void manualUpsertRejectsSameCurrency() {
    assertThrows(
        BusinessException.class,
        () ->
            service.upsertManual(
                LocalDate.of(2026, 8, 10), "JPY", "JPY", BigDecimal.ONE));
  }


  @Test
  void effectiveFallsBackToFxExecutedWhenMarketAndManualSnapshotsAreMissing() {
    ExchangeRate executed =
        rate("CNY", "JPY", "20.10", "FX_EXECUTED", LocalDate.of(2026, 8, 10));
    when(exchangeRateMapper.selectOne(any())).thenReturn(null, null, null, null, executed);

    var result =
        service.effective(LocalDate.of(2026, 8, 10), "CNY", "JPY", null);

    assertEquals(new BigDecimal("20.10"), result.exchangeRate());
    assertEquals("FX_EXECUTED", result.rateSourceCode());
  }

  @Test
  void realtimeDerivesCrossRateThroughUsdWhenDirectPairIsUnavailable() {
    when(tradingViewRateProvider.getQuote("CNY", "THB"))
        .thenThrow(
            new BusinessException(
                CommonErrorCode.DATA_NOT_FOUND, "TradingView 未返回可用汇率：CNY/THB"));
    when(tradingViewRateProvider.getQuote("CNY", "USD"))
        .thenReturn(
            new RealtimeExchangeRateVo(
                "CNY",
                "USD",
                new BigDecimal("0.139"),
                "TRADINGVIEW",
                Instant.parse("2026-08-19T01:00:00Z"),
                false));
    when(tradingViewRateProvider.getQuote("USD", "THB"))
        .thenReturn(
            new RealtimeExchangeRateVo(
                "USD",
                "THB",
                new BigDecimal("35.8"),
                "TRADINGVIEW",
                Instant.parse("2026-08-19T01:00:01Z"),
                false));

    var result = service.realtime("cny", "thb");

    assertEquals(new BigDecimal("4.9762000000"), result.exchangeRate());
    assertEquals("CNY", result.baseCurrency());
    assertEquals("THB", result.quoteCurrency());
    assertEquals("TRADINGVIEW", result.rateSourceCode());
    assertEquals(Instant.parse("2026-08-19T01:00:01Z"), result.observedAt());
    assertTrue(result.derived());
  }

  @Test
  void fxExecutedUpsertPersistsDedicatedRateSource() {
    when(exchangeRateMapper.selectOne(any())).thenReturn(null);
    when(exchangeRateMapper.insert(any(ExchangeRate.class))).thenReturn(1);

    var result =
        service.upsertFxExecuted(
            LocalDate.of(2026, 8, 10), "JPY", "CNY", new BigDecimal("0.05"));

    assertEquals("FX_EXECUTED", result.rateSourceCode());
    assertEquals(new BigDecimal("0.05"), result.exchangeRate());
  }
  private ExchangeRate rate(
      String base, String quote, String value, String source, LocalDate date) {
    ExchangeRate rate = new ExchangeRate();
    rate.setRateId("r1");
    rate.setRateDate(date);
    rate.setBaseCurrency(base);
    rate.setQuoteCurrency(quote);
    rate.setExchangeRate(new BigDecimal(value));
    rate.setRateSourceCode(source);
    rate.setCreatedAt(Instant.parse("2026-08-10T00:00:00Z"));
    return rate;
  }
}
