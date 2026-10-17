package com.jiajunxie.controller;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.convert.AnalyticsConvertWebMapper;
import com.jiajunxie.response.AnalyticsPeriodResponse;
import com.jiajunxie.service.AnalyticsService;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
  private final AnalyticsService analyticsService;
  private final AnalyticsConvertWebMapper analyticsConvertWebMapper;

  @GetMapping("/period")
  public ResponseEntity<ApiResponse<AnalyticsPeriodResponse>> period(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
      @RequestParam(defaultValue = "Asia/Tokyo") String timezone) {
    ZoneId zoneId = parseTimezone(timezone);
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取统计数据成功",
            analyticsConvertWebMapper.toResponse(
                analyticsService.getPeriod(start, end, zoneId))));
  }

  private ZoneId parseTimezone(String timezone) {
    try {
      return ZoneId.of(timezone);
    } catch (DateTimeException exception) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "无效的时区：" + timezone);
    }
  }
}
