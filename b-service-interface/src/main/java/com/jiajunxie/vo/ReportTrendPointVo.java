package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReportTrendPointVo(
    String periodKey,
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal income,
    BigDecimal expense,
    BigDecimal netSurplus,
    BigDecimal cumulativeNetSurplus,
    String currency) {}
