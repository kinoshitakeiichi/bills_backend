package com.jiajunxie.vo;

public record BudgetTargetVo(
    String budgetTargetId,
    String targetTypeCode,
    String targetId,
    String targetValue,
    Boolean includeDescendants) {}
