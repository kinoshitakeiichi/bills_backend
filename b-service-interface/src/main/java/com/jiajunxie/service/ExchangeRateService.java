package com.jiajunxie.service;

import com.jiajunxie.vo.ExchangeRateVo;
import com.jiajunxie.vo.RealtimeExchangeRateVo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExchangeRateService {

  ExchangeRateVo upsertManual(
      LocalDate rateDate, String baseCurrency, String quoteCurrency, BigDecimal exchangeRate);

  ExchangeRateVo upsertFxExecuted(
      LocalDate rateDate, String baseCurrency, String quoteCurrency, BigDecimal exchangeRate);

  List<ExchangeRateVo> list(
      String baseCurrency,
      String quoteCurrency,
      LocalDate dateFrom,
      LocalDate dateTo,
      String rateSourceCode);

  ExchangeRateVo exact(
      LocalDate rateDate, String baseCurrency, String quoteCurrency, String rateSourceCode);

  ExchangeRateVo effective(
      LocalDate valuationDate, String baseCurrency, String quoteCurrency, String rateSourceCode);

  RealtimeExchangeRateVo realtime(String baseCurrency, String quoteCurrency);

  ExchangeRateVo snapshotTradingView(
      LocalDate rateDate, String baseCurrency, String quoteCurrency);
}
