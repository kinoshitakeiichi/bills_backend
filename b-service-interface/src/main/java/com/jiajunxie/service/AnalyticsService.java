package com.jiajunxie.service;

import com.jiajunxie.vo.AnalyticsPeriodVo;
import java.time.LocalDate;
import java.time.ZoneId;

public interface AnalyticsService {
  AnalyticsPeriodVo getPeriod(
      LocalDate periodStart,
      LocalDate periodEnd,
      ZoneId timezone);
}
