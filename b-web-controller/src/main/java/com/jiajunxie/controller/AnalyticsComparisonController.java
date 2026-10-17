package com.jiajunxie.controller;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.convert.AnalyticsComparisonConvertWebMapper;
import com.jiajunxie.response.AnalyticsComparisonResponse;
import com.jiajunxie.service.AnalyticsComparisonService;
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
public class AnalyticsComparisonController {
  private final AnalyticsComparisonService analyticsComparisonService;
  private final AnalyticsComparisonConvertWebMapper analyticsComparisonConvertWebMapper;

  @GetMapping("/comparison")
  public ResponseEntity<ApiResponse<AnalyticsComparisonResponse>> comparison(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
      @RequestParam(defaultValue = "Asia/Tokyo") String timezone) {
    ZoneId zoneId = parseTimezone(timezone);
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取同比环比数据成功",
            analyticsComparisonConvertWebMapper.toResponse(
                analyticsComparisonService.getComparison(start, end, zoneId))));
  }

  private ZoneId parseTimezone(String timezone) {
    try {
      return ZoneId.of(timezone);
    } catch (DateTimeException exception) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "无效的时区：" + timezone);
    }
  }
}
