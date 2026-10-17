package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.util.List;

public record FxLegacyRebuildReportVo(
    String accountId,
    String accountName,
    String currency,
    String costCurrency,
    BigDecimal accountBalance,
    BigDecimal currentPositionQuantity,
    BigDecimal currentPositionCost,
    BigDecimal rebuiltQuantity,
    BigDecimal rebuiltTotalCost,
    BigDecimal rebuiltAverageCostRate,
    BigDecimal quantityDifference,
    BigDecimal costDifference,
    List<FxLegacyRebuildStepVo> steps) {}
