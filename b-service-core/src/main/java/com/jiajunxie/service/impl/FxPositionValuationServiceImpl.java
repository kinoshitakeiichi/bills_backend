package com.jiajunxie.service.impl;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.service.ExchangeRateService;
import com.jiajunxie.service.FxExchangeService;
import com.jiajunxie.service.FxPositionValuationService;
import com.jiajunxie.vo.ExchangeRateVo;
import com.jiajunxie.vo.FxPositionValuationVo;
import com.jiajunxie.vo.FxPositionVo;
import com.jiajunxie.vo.RealtimeExchangeRateVo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class FxPositionValuationServiceImpl implements FxPositionValuationService {

  private static final int MONEY_SCALE = 4;
  private static final int RETURN_RATE_SCALE = 10;

  private final FxExchangeService fxExchangeService;
  private final ExchangeRateService exchangeRateService;

  @Override
  public FxPositionValuationVo realtime(String accountId) {
    FxPositionVo position = requirePosition(accountId);
    RealtimeExchangeRateVo rate =
        exchangeRateService.realtime(position.currency(), position.costCurrency());

    return calculate(
        position,
        "REALTIME",
        LocalDate.now(),
        rate.exchangeRate(),
        rate.rateSourceCode(),
        null,
        rate.observedAt(),
        rate.derived());
  }

  @Override
  public FxPositionValuationVo reference(
      String accountId, LocalDate valuationDate, String rateSourceCode) {
    if (valuationDate == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "估值日期不能为空");
    }

    FxPositionVo position = requirePosition(accountId);
    ExchangeRateVo rate =
        exchangeRateService.effective(
            valuationDate, position.currency(), position.costCurrency(), rateSourceCode);

    return calculate(
        position,
        "STORED",
        valuationDate,
        rate.exchangeRate(),
        rate.rateSourceCode(),
        rate.rateDate(),
        null,
        rate.derived());
  }

  private FxPositionVo requirePosition(String accountId) {
    if (!StringUtils.hasText(accountId)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "账户 ID 不能为空");
    }
    FxPositionVo position = fxExchangeService.getPosition(accountId);
    if (position == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "账户暂无 FX Position");
    }
    return position;
  }

  private FxPositionValuationVo calculate(
      FxPositionVo position,
      String valuationMode,
      LocalDate valuationDate,
      BigDecimal effectiveRate,
      String rateSourceCode,
      LocalDate rateDate,
      java.time.Instant observedAt,
      boolean derivedRate) {
    if (effectiveRate == null || effectiveRate.signum() <= 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "估值汇率必须大于 0");
    }

    BigDecimal marketValue =
        position.quantity().multiply(effectiveRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    BigDecimal unrealizedGainLoss =
        marketValue.subtract(position.totalCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    BigDecimal unrealizedReturnRate =
        position.totalCost().signum() == 0
            ? BigDecimal.ZERO.setScale(RETURN_RATE_SCALE, RoundingMode.HALF_UP)
            : unrealizedGainLoss
                .divide(position.totalCost(), RETURN_RATE_SCALE, RoundingMode.HALF_UP);

    return new FxPositionValuationVo(
        position.positionId(),
        position.accountId(),
        position.currency(),
        position.quantity(),
        position.totalCost(),
        position.costCurrency(),
        position.averageCostRate(),
        valuationMode,
        valuationDate,
        effectiveRate,
        rateSourceCode,
        rateDate,
        observedAt,
        derivedRate,
        marketValue,
        unrealizedGainLoss,
        unrealizedReturnRate);
  }
}
