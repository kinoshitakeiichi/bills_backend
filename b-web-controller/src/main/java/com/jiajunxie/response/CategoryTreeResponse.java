package com.jiajunxie.response;

import java.util.List;

public record CategoryTreeResponse(
    String value,
    String label,
    String categoryCode,
    String categoryTypeCode,
    String iconCode,
    boolean leaf,
    List<CategoryTreeResponse> children) {}
