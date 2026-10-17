package com.jiajunxie.response;

public record BudgetTargetResponse(
    String budgetTargetId,
    String targetTypeCode,
    String targetId,
    String targetValue,
    Boolean includeDescendants) {}
