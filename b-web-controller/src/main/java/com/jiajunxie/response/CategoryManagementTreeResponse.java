package com.jiajunxie.response;

import java.util.List;

public record CategoryManagementTreeResponse(
    String value,
    String label,
    String categoryCode,
    String categoryTypeCode,
    String iconCode,
    String parentId,
    Integer sortOrder,
    boolean enabled,
    boolean leaf,
    List<CategoryManagementTreeResponse> children) {}
