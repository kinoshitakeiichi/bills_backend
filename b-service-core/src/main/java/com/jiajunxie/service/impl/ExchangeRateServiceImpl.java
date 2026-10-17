package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.ExchangeRate;
import com.jiajunxie.mapper.ExchangeRateMapper;
import com.jiajunxie.service.DictionaryService;
import com.jiajunxie.service.ExchangeRateService;
import com.jiajunxie.service.TradingViewRateProvider;
import com.jiajunxie.vo.DictionaryOptionVo;
import com.jiajunxie.vo.ExchangeRateVo;
import com.jiajunxie.vo.RealtimeExchangeRateVo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ExchangeRateServiceImpl implements ExchangeRateService {

  private static final String RATE_SOURCE_MANUAL = "MANUAL";
  private static final String RATE_SOURCE_TRADINGVIEW = "TRADINGVIEW";
  private static final String RATE_SOURCE_FX_EXECUTED = "FX_EXECUTED";
  private static final int INVERSE_SCALE = 10;
  private static final int CROSS_SCALE = 10;
  private static final List<String> REALTIME_CROSS_PIVOTS = List.of("USD", "JPY", "EUR");

  private final ExchangeRateMapper exchangeRateMapper;
  private final DictionaryService dictionaryService;
  private final TradingViewRateProvider tradingViewRateProvider;

  @Override
  @Transactional
  public ExchangeRateVo upsertManual(
      LocalDate rateDate,
      String baseCurrency,
      String quoteCurrency,
      BigDecimal exchangeRate) {
    validateRate(rateDate, baseCurrency, quoteCurrency, exchangeRate);
    requireRateSource(RATE_SOURCE_MANUAL);
    return toVo(
        upsertStored(
            rateDate,
            normalizeCurrency(baseCurrency),
            normalizeCurrency(quoteCurrency),
            exchangeRate,
            RATE_SOURCE_MANUAL),
        false);
  }

  @Override
  @Transactional
  public ExchangeRateVo upsertFxExecuted(
      LocalDate rateDate,
      String baseCurrency,
      String quoteCurrency,
      BigDecimal exchangeRate) {
    validateRate(rateDate, baseCurrency, quoteCurrency, exchangeRate);
    requireRateSource(RATE_SOURCE_FX_EXECUTED);
    return toVo(
        upsertStored(
            rateDate,
            normalizeCurrency(baseCurrency),
            normalizeCurrency(quoteCurrency),
            exchangeRate,
            RATE_SOURCE_FX_EXECUTED),
        false);
  }

  @Override
  public List<ExchangeRateVo> list(
      String baseCurrency,
      String quoteCurrency,
      LocalDate dateFrom,
      LocalDate dateTo,
      String rateSourceCode) {
    String base = normalizeNullableCurrency(baseCurrency);
    String quote = normalizeNullableCurrency(quoteCurrency);
    String source = normalizeNullableCode(rateSourceCode);
    if (source != null) requireRateSource(source);
    if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "开始日期不能晚于结束日期");
    }

    return exchangeRateMapper
        .selectList(
            Wrappers.<ExchangeRate>lambdaQuery()
                .eq(base != null, ExchangeRate::getBaseCurrency, base)
                .eq(quote != null, ExchangeRate::getQuoteCurrency, quote)
                .ge(dateFrom != null, ExchangeRate::getRateDate, dateFrom)
                .le(dateTo != null, ExchangeRate::getRateDate, dateTo)
                .eq(source != null, ExchangeRate::getRateSourceCode, source)
                .orderByDesc(ExchangeRate::getRateDate)
                .orderByAsc(ExchangeRate::getBaseCurrency)
                .orderByAsc(ExchangeRate::getQuoteCurrency)
                .orderByAsc(ExchangeRate::getRateSourceCode))
        .stream()
        .map(rate -> toVo(rate, false))
        .toList();
  }

  @Override
  public ExchangeRateVo exact(
      LocalDate rateDate,
      String baseCurrency,
      String quoteCurrency,
      String rateSourceCode) {
    if (rateDate == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "汇率日期不能为空");
    }
    String base = normalizeCurrency(baseCurrency);
    String quote = normalizeCurrency(quoteCurrency);
    String source = normalizeSourceOrDefault(rateSourceCode);

    if (base.equals(quote)) {
      return identity(rateDate, base, source);
    }

    ExchangeRate direct = selectExact(rateDate, base, quote, source);
    if (direct != null) return toVo(direct, false);

    ExchangeRate inverse = selectExact(rateDate, quote, base, source);
    if (inverse != null) return inverse(inverse, base, quote);

    throw new BusinessException(
        CommonErrorCode.DATA_NOT_FOUND,
        "未找到指定日期汇率：" + base + "/" + quote + " " + rateDate);
  }

  @Override
  public ExchangeRateVo effective(
      LocalDate valuationDate,
      String baseCurrency,
      String quoteCurrency,
      String rateSourceCode) {
    if (valuationDate == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "估值日期不能为空");
    }
    String base = normalizeCurrency(baseCurrency);
    String quote = normalizeCurrency(quoteCurrency);
    String requestedSource = normalizeNullableCode(rateSourceCode);

    if (base.equals(quote)) {
      if (requestedSource != null) requireRateSource(requestedSource);
      return identity(valuationDate, base, requestedSource);
    }

    if (requestedSource != null) {
      requireRateSource(requestedSource);
      ExchangeRateVo explicit =
          findEffectiveVoBySource(valuationDate, base, quote, requestedSource);
      if (explicit != null) return explicit;
    } else {
      requireRateSource(RATE_SOURCE_TRADINGVIEW);
      ExchangeRateVo tradingView =
          findEffectiveVoBySource(valuationDate, base, quote, RATE_SOURCE_TRADINGVIEW);
      if (tradingView != null) return tradingView;

      requireRateSource(RATE_SOURCE_MANUAL);
      ExchangeRateVo manual =
          findEffectiveVoBySource(valuationDate, base, quote, RATE_SOURCE_MANUAL);
      if (manual != null) return manual;

      // FX transactions already carry an executed accounting rate. Keep it as the last fallback
      // so Budget/analytics remain replayable even when no market/manual snapshot exists.
      requireRateSource(RATE_SOURCE_FX_EXECUTED);
      ExchangeRateVo fxExecuted =
          findEffectiveVoBySource(valuationDate, base, quote, RATE_SOURCE_FX_EXECUTED);
      if (fxExecuted != null) return fxExecuted;
    }

    throw new BusinessException(
        CommonErrorCode.DATA_NOT_FOUND,
        "未找到有效汇率：" + base + "/" + quote + " <= " + valuationDate);
  }

  @Override
  public RealtimeExchangeRateVo realtime(String baseCurrency, String quoteCurrency) {
    String base = normalizeCurrency(baseCurrency);
    String quote = normalizeCurrency(quoteCurrency);
    if (base.equals(quote)) {
      return new RealtimeExchangeRateVo(
          base, quote, BigDecimal.ONE, RATE_SOURCE_TRADINGVIEW, Instant.now(), true);
    }

    BusinessException directFailure;
    try {
      return tradingViewRateProvider.getQuote(base, quote);
    } catch (BusinessException ex) {
      if (!CommonErrorCode.DATA_NOT_FOUND.getCode().equals(ex.getCode())) throw ex;
      directFailure = ex;
    }

    for (String pivot : REALTIME_CROSS_PIVOTS) {
      if (pivot.equals(base) || pivot.equals(quote)) continue;
      try {
        RealtimeExchangeRateVo first = tradingViewRateProvider.getQuote(base, pivot);
        RealtimeExchangeRateVo second = tradingViewRateProvider.getQuote(pivot, quote);
        BigDecimal cross =
            first.exchangeRate()
                .multiply(second.exchangeRate())
                .setScale(CROSS_SCALE, RoundingMode.HALF_UP);
        Instant observedAt =
            first.observedAt().isAfter(second.observedAt()) ? first.observedAt() : second.observedAt();
        return new RealtimeExchangeRateVo(
            base, quote, cross, RATE_SOURCE_TRADINGVIEW, observedAt, true);
      } catch (BusinessException ex) {
        if (!CommonErrorCode.DATA_NOT_FOUND.getCode().equals(ex.getCode())) throw ex;
      }
    }

    throw directFailure;
  }

  @Override
  @Transactional
  public ExchangeRateVo snapshotTradingView(
      LocalDate rateDate, String baseCurrency, String quoteCurrency) {
    LocalDate targetDate = rateDate == null ? LocalDate.now() : rateDate;
    String base = normalizeCurrency(baseCurrency);
    String quote = normalizeCurrency(quoteCurrency);
    if (base.equals(quote)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "同币种汇率不需要持久化");
    }

    requireRateSource(RATE_SOURCE_TRADINGVIEW);
    RealtimeExchangeRateVo quoteVo = tradingViewRateProvider.getQuote(base, quote);

    ExchangeRate stored =
        upsertStored(
            targetDate,
            base,
            quote,
            quoteVo.exchangeRate(),
            RATE_SOURCE_TRADINGVIEW);
    return toVo(stored, quoteVo.derived());
  }

  private ExchangeRate upsertStored(
      LocalDate rateDate,
      String baseCurrency,
      String quoteCurrency,
      BigDecimal exchangeRate,
      String rateSourceCode) {
    ExchangeRate existing =
        selectExact(rateDate, baseCurrency, quoteCurrency, rateSourceCode);
    if (existing == null) {
      ExchangeRate entity = new ExchangeRate();
      entity.setRateId(UUID.randomUUID().toString());
      entity.setRateDate(rateDate);
      entity.setBaseCurrency(baseCurrency);
      entity.setQuoteCurrency(quoteCurrency);
      entity.setExchangeRate(exchangeRate);
      entity.setRateSourceCode(rateSourceCode);
      entity.setCreatedAt(Instant.now());
      exchangeRateMapper.insert(entity);
      return entity;
    }

    existing.setExchangeRate(exchangeRate);
    exchangeRateMapper.updateById(existing);
    return existing;
  }

  private ExchangeRate selectExact(
      LocalDate rateDate, String base, String quote, String source) {
    return exchangeRateMapper.selectOne(
        Wrappers.<ExchangeRate>lambdaQuery()
            .eq(ExchangeRate::getRateDate, rateDate)
            .eq(ExchangeRate::getBaseCurrency, base)
            .eq(ExchangeRate::getQuoteCurrency, quote)
            .eq(ExchangeRate::getRateSourceCode, source));
  }

  private ExchangeRateVo findEffectiveVoBySource(
      LocalDate valuationDate, String base, String quote, String source) {
    ExchangeRate direct = selectEffective(valuationDate, base, quote, source);
    if (direct != null) return toVo(direct, false);

    ExchangeRate inverse = selectEffective(valuationDate, quote, base, source);
    return inverse == null ? null : inverse(inverse, base, quote);
  }

  private ExchangeRate selectEffective(
      LocalDate valuationDate, String base, String quote, String source) {
    return exchangeRateMapper.selectOne(
        Wrappers.<ExchangeRate>lambdaQuery()
            .eq(ExchangeRate::getBaseCurrency, base)
            .eq(ExchangeRate::getQuoteCurrency, quote)
            .eq(ExchangeRate::getRateSourceCode, source)
            .le(ExchangeRate::getRateDate, valuationDate)
            .orderByDesc(ExchangeRate::getRateDate)
            .last("LIMIT 1"));
  }

  private ExchangeRateVo inverse(ExchangeRate stored, String base, String quote) {
    BigDecimal value =
        BigDecimal.ONE.divide(stored.getExchangeRate(), INVERSE_SCALE, RoundingMode.HALF_UP);
    return new ExchangeRateVo(
        stored.getRateId(),
        stored.getRateDate(),
        base,
        quote,
        value,
        stored.getRateSourceCode(),
        stored.getCreatedAt(),
        true);
  }

  private ExchangeRateVo identity(LocalDate date, String currency, String source) {
    return new ExchangeRateVo(
        null, date, currency, currency, BigDecimal.ONE, source, null, true);
  }

  private ExchangeRateVo toVo(ExchangeRate entity, boolean derived) {
    return new ExchangeRateVo(
        entity.getRateId(),
        entity.getRateDate(),
        entity.getBaseCurrency(),
        entity.getQuoteCurrency(),
        entity.getExchangeRate(),
        entity.getRateSourceCode(),
        entity.getCreatedAt(),
        derived);
  }

  private String normalizeSourceOrDefault(String value) {
    String source = normalizeNullableCode(value);
    source = source == null ? RATE_SOURCE_MANUAL : source;
    requireRateSource(source);
    return source;
  }

  private void requireRateSource(String sourceCode) {
    List<DictionaryOptionVo> options = dictionaryService.listOptions("RATE_SOURCE", sourceCode);
    boolean valid =
        options.stream()
            .anyMatch(option -> sourceCode.equalsIgnoreCase(option.getValue()));
    if (!valid) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR, "汇率来源未启用或不存在：" + sourceCode);
    }
  }

  private void validateRate(
      LocalDate rateDate,
      String baseCurrency,
      String quoteCurrency,
      BigDecimal exchangeRate) {
    if (rateDate == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "汇率日期不能为空");
    }
    String base = normalizeCurrency(baseCurrency);
    String quote = normalizeCurrency(quoteCurrency);
    if (base.equals(quote)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "基础币种与报价币种不能相同");
    }
    if (exchangeRate == null || exchangeRate.signum() <= 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "汇率必须大于 0");
    }
  }

  private String normalizeCurrency(String value) {
    if (!StringUtils.hasText(value)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "币种不能为空");
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeNullableCurrency(String value) {
    return StringUtils.hasText(value) ? normalizeCurrency(value) : null;
  }

  private String normalizeNullableCode(String value) {
    return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
  }
}
