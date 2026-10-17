package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.util.List;

public record BudgetItemVo(
    String budgetItemId,
    BigDecimal budgetAmount,
    String currency,
    Boolean rolloverEnabled,
    Integer sortOrder,
    List<BudgetTargetVo> targets) {}
