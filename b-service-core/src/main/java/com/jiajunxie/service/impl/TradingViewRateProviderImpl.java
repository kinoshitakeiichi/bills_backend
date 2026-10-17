package com.jiajunxie.service.impl;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.service.TradingViewRateProvider;
import com.jiajunxie.vo.RealtimeExchangeRateVo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestOperations;

@Component
public class TradingViewRateProviderImpl implements TradingViewRateProvider {

  private static final String SCAN_URL = "https://scanner.tradingview.com/forex/scan";
  private static final String RATE_SOURCE = "TRADINGVIEW";
  private static final int INVERSE_SCALE = 10;

  private final RestOperations restOperations;

  public TradingViewRateProviderImpl() {
    this(new org.springframework.web.client.RestTemplate());
  }

  TradingViewRateProviderImpl(RestOperations restOperations) {
    this.restOperations = restOperations;
  }

  @Override
  public RealtimeExchangeRateVo getQuote(String baseCurrency, String quoteCurrency) {
    String base = normalizeCurrency(baseCurrency);
    String quote = normalizeCurrency(quoteCurrency);

    if (base.equals(quote)) {
      return new RealtimeExchangeRateVo(
          base, quote, BigDecimal.ONE, RATE_SOURCE, Instant.now(), true);
    }

    BigDecimal direct = fetch(base + quote);
    if (direct != null) {
      return new RealtimeExchangeRateVo(
          base, quote, direct, RATE_SOURCE, Instant.now(), false);
    }

    BigDecimal inverse = fetch(quote + base);
    if (inverse != null) {
      return new RealtimeExchangeRateVo(
          base,
          quote,
          BigDecimal.ONE.divide(inverse, INVERSE_SCALE, RoundingMode.HALF_UP),
          RATE_SOURCE,
          Instant.now(),
          true);
    }

    throw new BusinessException(
        CommonErrorCode.DATA_NOT_FOUND,
        "TradingView 未返回可用汇率：" + base + "/" + quote);
  }

  private BigDecimal fetch(String pair) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set(
          "User-Agent",
          "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");

      Map<String, Object> payload =
          Map.of(
              "markets", List.of("forex"),
              "symbols",
                  Map.of(
                      "tickers",
                      List.of("FX_IDC:" + pair),
                      "query",
                      Map.of("types", List.of())),
              "columns",
              List.of("close"));

      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

      @SuppressWarnings("rawtypes")
      Map response = restOperations.postForObject(SCAN_URL, entity, Map.class);
      if (response == null || !(response.get("data") instanceof List<?> data) || data.isEmpty()) {
        return null;
      }
      if (!(data.get(0) instanceof Map<?, ?> first)
          || !(first.get("d") instanceof List<?> values)
          || values.isEmpty()
          || values.get(0) == null) {
        return null;
      }

      BigDecimal value = new BigDecimal(values.get(0).toString());
      return value.signum() > 0 ? value : null;
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private String normalizeCurrency(String value) {
    if (!StringUtils.hasText(value)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "币种不能为空");
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!normalized.matches("[A-Z]{3}")) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR, "TradingView 币种必须为 3 位 ISO 代码：" + value);
    }
    return normalized;
  }
}
