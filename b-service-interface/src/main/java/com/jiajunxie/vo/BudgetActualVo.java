package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BudgetActualVo(
    String budgetId,
    String budgetName,
    LocalDate periodStart,
    LocalDate periodEnd,
    String baseCurrency,
    List<ItemActual> items) {

  public record ItemActual(
      String budgetItemId,
      BigDecimal budgetAmount,
      BigDecimal actualAmount,
      BigDecimal remainingAmount,
      String currency,
      Boolean rolloverEnabled,
      Integer sortOrder,
      List<BudgetTargetVo> targets) {}
}
