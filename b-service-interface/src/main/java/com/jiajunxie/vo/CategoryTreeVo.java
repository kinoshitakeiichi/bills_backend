package com.jiajunxie.vo;

import java.util.List;

public record CategoryTreeVo(
    String value,
    String label,
    String categoryCode,
    String categoryTypeCode,
    String iconCode,
    boolean leaf,
    List<CategoryTreeVo> children) {}
