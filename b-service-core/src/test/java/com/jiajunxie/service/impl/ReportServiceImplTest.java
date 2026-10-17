package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jiajunxie.domain.transaction.TransactionLedgerPage;
import com.jiajunxie.domain.transaction.TransactionLedgerQueryCriteria;
import com.jiajunxie.domain.transaction.TransactionLedgerRow;
import com.jiajunxie.domain.transaction.TransactionNature;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.service.ExchangeRateService;
import com.jiajunxie.service.TransactionLedgerQueryService;
import com.jiajunxie.vo.RealtimeExchangeRateVo;
import com.jiajunxie.vo.ReportQueryVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReportServiceImplTest {

  @Test
  void computesCurrentAndHistoricalCumulativeSurplusWithQueryTimeRealtimeConversion() {
    TransactionLedgerQueryService ledger = mock(TransactionLedgerQueryService.class);
    ExchangeRateService exchangeRate = mock(ExchangeRateService.class);
    CategoryMapper categoryMapper = mock(CategoryMapper.class);

    when(categoryMapper.selectList(any())).thenReturn(List.of());
    stubLedger(
        ledger,
        Instant.parse("2026-06-30T15:00:00Z"),
        List.of(
            row("income", "2026-07-10T00:00:00Z", "JPY", "1000", TransactionNature.INCOME),
            row("expense-usd", "2026-07-11T00:00:00Z", "USD", "2", TransactionNature.EXPENSE),
            row("refund", "2026-08-01T00:00:00Z", "JPY", "100", TransactionNature.REFUND)),
        List.of(
            row("old-income", "2026-05-01T00:00:00Z", "JPY", "5000", TransactionNature.INCOME),
            row("old-expense", "2026-05-02T00:00:00Z", "JPY", "1000", TransactionNature.EXPENSE)));

    when(exchangeRate.realtime("USD", "JPY"))
        .thenReturn(
            new RealtimeExchangeRateVo(
                "USD",
                "JPY",
                new BigDecimal("150"),
                "TRADINGVIEW",
                Instant.parse("2026-08-19T00:00:00Z"),
                false));

    ReportServiceImpl service = new ReportServiceImpl(ledger, exchangeRate, categoryMapper);
    var result =
        service.overview(
            new ReportQueryVo(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 8, 31),
                "MONTH",
                "JPY",
                List.of(),
                List.of(),
                List.of()));

    assertEquals("REALTIME_CONVERTED", result.valuationMode());
    assertEquals(new BigDecimal("1000"), result.totalIncome());
    assertEquals(new BigDecimal("200"), result.totalExpense());
    assertEquals(new BigDecimal("800"), result.netSurplus());
    assertEquals(new BigDecimal("4800"), result.cumulativeNetSurplus());
    assertEquals(new BigDecimal("4700"), result.trend().get(0).cumulativeNetSurplus());
    assertEquals(new BigDecimal("4800"), result.trend().get(1).cumulativeNetSurplus());
    assertEquals(1, result.rates().size());
    verify(exchangeRate).realtime("USD", "JPY");
    verify(exchangeRate, never()).effective(any(), anyString(), anyString(), any());
  }

  @Test
  void usesReportTimezoneForLedgerBoundaryAndBusinessDateWithoutHistoricalRateLookup() {
    TransactionLedgerQueryService ledger = mock(TransactionLedgerQueryService.class);
    ExchangeRateService exchangeRate = mock(ExchangeRateService.class);
    CategoryMapper categoryMapper = mock(CategoryMapper.class);

    when(categoryMapper.selectList(any())).thenReturn(List.of());
    stubLedger(
        ledger,
        Instant.parse("2026-06-28T15:00:00Z"),
        List.of(
            row(
                "opening-cny",
                "2026-06-28T15:00:00Z",
                "CNY",
                "10",
                TransactionNature.INCOME)),
        List.of());
    when(exchangeRate.realtime("CNY", "JPY"))
        .thenReturn(
            new RealtimeExchangeRateVo(
                "CNY",
                "JPY",
                new BigDecimal("22"),
                "TRADINGVIEW",
                Instant.parse("2026-08-19T00:00:00Z"),
                false));

    ReportServiceImpl service = new ReportServiceImpl(ledger, exchangeRate, categoryMapper);
    var result =
        service.overview(
            new ReportQueryVo(
                LocalDate.of(2026, 6, 29),
                LocalDate.of(2026, 6, 29),
                "DAY",
                "JPY",
                List.of(),
                List.of(),
                List.of(),
                ZoneId.of("Asia/Tokyo")));

    ArgumentCaptor<TransactionLedgerQueryCriteria> criteria =
        ArgumentCaptor.forClass(TransactionLedgerQueryCriteria.class);
    verify(ledger, times(2)).query(criteria.capture());
    TransactionLedgerQueryCriteria currentCriteria =
        criteria.getAllValues().stream()
            .filter(value -> value.startInclusive().equals(Instant.parse("2026-06-28T15:00:00Z")))
            .findFirst()
            .orElseThrow();
    assertEquals(Instant.parse("2026-06-28T15:00:00Z"), currentCriteria.startInclusive());
    assertEquals(Instant.parse("2026-06-29T15:00:00Z"), currentCriteria.endExclusive());
    assertEquals(new BigDecimal("220"), result.totalIncome());
    assertEquals("2026-06-29", result.trend().get(0).periodKey());
    assertEquals(new BigDecimal("220"), result.trend().get(0).income());
    verify(exchangeRate).realtime("CNY", "JPY");
    verify(exchangeRate, never()).effective(any(), anyString(), anyString(), any());
  }

  @Test
  void arbitraryReportBaseCurrencyUsesRealtimeQuotesAndReusesThemAcrossDates() {
    TransactionLedgerQueryService ledger = mock(TransactionLedgerQueryService.class);
    ExchangeRateService exchangeRate = mock(ExchangeRateService.class);
    CategoryMapper categoryMapper = mock(CategoryMapper.class);

    when(categoryMapper.selectList(any())).thenReturn(List.of());
    stubLedger(
        ledger,
        Instant.parse("2026-06-30T15:00:00Z"),
        List.of(
            row("jpy-1", "2026-07-10T00:00:00Z", "JPY", "1000", TransactionNature.INCOME),
            row("jpy-2", "2026-07-11T00:00:00Z", "JPY", "500", TransactionNature.INCOME),
            row("cny-1", "2026-07-12T00:00:00Z", "CNY", "100", TransactionNature.INCOME)),
        List.of());
    when(exchangeRate.realtime("JPY", "THB"))
        .thenReturn(quote("JPY", "THB", "0.225"));
    when(exchangeRate.realtime("CNY", "THB"))
        .thenReturn(quote("CNY", "THB", "4.95"));

    ReportServiceImpl service = new ReportServiceImpl(ledger, exchangeRate, categoryMapper);
    var result =
        service.overview(
            new ReportQueryVo(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                "MONTH",
                "THB",
                List.of(),
                List.of(),
                List.of(),
                ZoneId.of("Asia/Tokyo")));

    assertEquals("THB", result.baseCurrency());
    assertEquals("REALTIME_CONVERTED", result.valuationMode());
    assertEquals(new BigDecimal("832.5"), result.totalIncome());
    assertEquals(2, result.rates().size());
    verify(exchangeRate).realtime("JPY", "THB");
    verify(exchangeRate).realtime("CNY", "THB");
    verify(exchangeRate, never()).effective(any(), anyString(), anyString(), any());
  }

  @Test
  void nonReportingForeignTransactionDoesNotRequestExchangeRate() {
    TransactionLedgerQueryService ledger = mock(TransactionLedgerQueryService.class);
    ExchangeRateService exchangeRate = mock(ExchangeRateService.class);
    CategoryMapper categoryMapper = mock(CategoryMapper.class);

    when(categoryMapper.selectList(any())).thenReturn(List.of());
    stubLedger(
        ledger,
        Instant.parse("2026-06-28T15:00:00Z"),
        List.of(
            row(
                "transfer-cny",
                "2026-06-28T15:00:00Z",
                "CNY",
                "12.4113",
                TransactionNature.INTERNAL_TRANSFER)),
        List.of());

    ReportServiceImpl service = new ReportServiceImpl(ledger, exchangeRate, categoryMapper);
    var result =
        service.overview(
            new ReportQueryVo(
                LocalDate.of(2026, 6, 29),
                LocalDate.of(2026, 6, 29),
                "DAY",
                "JPY",
                List.of(),
                List.of(),
                List.of(),
                ZoneId.of("Asia/Tokyo")));

    assertEquals(BigDecimal.ZERO, result.totalIncome());
    assertEquals(BigDecimal.ZERO, result.totalExpense());
    assertEquals(BigDecimal.ZERO, result.netSurplus());
    assertEquals(0, result.rates().size());
    verifyNoInteractions(exchangeRate);
  }

  private void stubLedger(
      TransactionLedgerQueryService ledger,
      Instant currentStart,
      List<TransactionLedgerRow> currentRows,
      List<TransactionLedgerRow> historicalRows) {
    when(ledger.query(any()))
        .thenAnswer(
            invocation -> {
              TransactionLedgerQueryCriteria criteria = invocation.getArgument(0);
              List<TransactionLedgerRow> rows =
                  criteria.startInclusive().equals(currentStart) ? currentRows : historicalRows;
              return new TransactionLedgerPage(rows, 1, 200, rows.size(), rows.isEmpty() ? 0 : 1);
            });
  }

  private RealtimeExchangeRateVo quote(String base, String quote, String value) {
    return new RealtimeExchangeRateVo(
        base,
        quote,
        new BigDecimal(value),
        "TRADINGVIEW",
        Instant.parse("2026-08-19T00:00:00Z"),
        false);
  }

  private TransactionLedgerRow row(
      String id, String time, String currency, String amount, TransactionNature nature) {
    return new TransactionLedgerRow(
        id,
        Instant.parse(time),
        Instant.parse(time),
        nature.name(),
        nature.name(),
        null,
        null,
        null,
        null,
        null,
        null,
        currency,
        new BigDecimal(amount),
        null,
        nature,
        null,
        null,
        null,
        List.of(),
        List.of(),
        false,
        null);
  }
}
