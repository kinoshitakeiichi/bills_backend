package com.jiajunxie.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BudgetActualResponse(
    String budgetId,
    String budgetName,
    LocalDate periodStart,
    LocalDate periodEnd,
    String baseCurrency,
    List<ItemActualResponse> items) {

  public record ItemActualResponse(
      String budgetItemId,
      BigDecimal budgetAmount,
      BigDecimal actualAmount,
      BigDecimal remainingAmount,
      String currency,
      Boolean rolloverEnabled,
      Integer sortOrder,
      List<BudgetTargetResponse> targets) {}
}
