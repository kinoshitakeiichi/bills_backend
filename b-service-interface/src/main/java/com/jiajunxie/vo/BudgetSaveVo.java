package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BudgetSaveVo(
    String budgetName,
    String periodTypeCode,
    LocalDate periodStart,
    LocalDate periodEnd,
    String baseCurrency,
    Boolean enabled,
    List<Item> items) {

  public record Item(
      BigDecimal budgetAmount,
      String currency,
      Boolean rolloverEnabled,
      Integer sortOrder,
      List<Target> targets) {}

  public record Target(
      String targetTypeCode,
      String targetId,
      String targetValue,
      Boolean includeDescendants) {}
}
