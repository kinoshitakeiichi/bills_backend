package com.jiajunxie.controller;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.request.ReportQueryRequest;
import com.jiajunxie.service.ReportService;
import com.jiajunxie.vo.ReportOverviewVo;
import com.jiajunxie.vo.ReportQueryVo;
import jakarta.validation.Valid;
import java.time.DateTimeException;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

  private static final String DEFAULT_TIMEZONE = "Asia/Tokyo";

  private final ReportService reportService;

  @PostMapping("/overview")
  public ResponseEntity<ApiResponse<ReportOverviewVo>> overview(
      @Valid @RequestBody ReportQueryRequest request) {
    ZoneId timezone = parseTimezone(request.timezone());
    return ResponseEntity.ok(
        ApiResponse.success(
            "报表查询成功",
            reportService.overview(
                new ReportQueryVo(
                    request.dateFrom(),
                    request.dateTo(),
                    request.granularity(),
                    request.baseCurrency(),
                    request.accountIds(),
                    request.categoryIds(),
                    request.personIds(),
                    timezone))));
  }

  private ZoneId parseTimezone(String timezone) {
    String normalized = StringUtils.hasText(timezone) ? timezone.trim() : DEFAULT_TIMEZONE;
    try {
      return ZoneId.of(normalized);
    } catch (DateTimeException exception) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "无效的时区：" + normalized);
    }
  }
}
