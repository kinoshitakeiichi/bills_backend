package com.jiajunxie.service;

import com.jiajunxie.vo.AnalyticsComparisonVo;
import java.time.LocalDate;
import java.time.ZoneId;

public interface AnalyticsComparisonService {
  AnalyticsComparisonVo getComparison(
      LocalDate periodStart,
      LocalDate periodEnd,
      ZoneId timezone);
}
