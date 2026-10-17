package com.jiajunxie.vo;

import java.time.LocalDate;
import java.util.List;

public record BudgetVo(
    String budgetId,
    String budgetName,
    String periodTypeCode,
    LocalDate periodStart,
    LocalDate periodEnd,
    String baseCurrency,
    Boolean enabled,
    List<BudgetItemVo> items) {}
