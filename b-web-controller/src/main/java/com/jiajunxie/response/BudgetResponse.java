package com.jiajunxie.response;

import java.time.LocalDate;
import java.util.List;

public record BudgetResponse(
    String budgetId,
    String budgetName,
    String periodTypeCode,
    LocalDate periodStart,
    LocalDate periodEnd,
    String baseCurrency,
    Boolean enabled,
    List<BudgetItemResponse> items) {}
