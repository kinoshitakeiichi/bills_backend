package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.util.List;

public record ReportCategoryNodeVo(
    String categoryId,
    String categoryName,
    String categoryTypeCode,
    BigDecimal amount,
    BigDecimal share,
    String currency,
    List<ReportCategoryNodeVo> children) {}
