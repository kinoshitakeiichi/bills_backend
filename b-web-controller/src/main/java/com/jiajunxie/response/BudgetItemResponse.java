package com.jiajunxie.response;

import java.math.BigDecimal;
import java.util.List;

public record BudgetItemResponse(
    String budgetItemId,
    BigDecimal budgetAmount,
    String currency,
    Boolean rolloverEnabled,
    Integer sortOrder,
    List<BudgetTargetResponse> targets) {}
