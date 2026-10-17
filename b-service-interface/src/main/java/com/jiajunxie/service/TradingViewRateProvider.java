package com.jiajunxie.service;

import com.jiajunxie.vo.RealtimeExchangeRateVo;

public interface TradingViewRateProvider {
  RealtimeExchangeRateVo getQuote(String baseCurrency, String quoteCurrency);
}
