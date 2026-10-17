package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReportOverviewVo(
    String baseCurrency,
    String valuationMode,
    Instant valuedAt,
    BigDecimal totalIncome,
    BigDecimal totalExpense,
    BigDecimal netSurplus,
    BigDecimal cumulativeNetSurplus,
    List<ReportTrendPointVo> trend,
    List<ReportCategoryNodeVo> expenseCategories,
    List<ReportRateSnapshotVo> rates) {}
