package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.convert.FxPositionValuationConvertWebMapper;
import com.jiajunxie.response.FxPositionValuationResponse;
import com.jiajunxie.service.FxPositionValuationService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fx/positions")
@RequiredArgsConstructor
public class FxPositionValuationController {

  private final FxPositionValuationService valuationService;
  private final FxPositionValuationConvertWebMapper convertMapper;

  @GetMapping("/{accountId}/valuation/realtime")
  public ResponseEntity<ApiResponse<FxPositionValuationResponse>> realtime(
      @PathVariable String accountId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取 FX Position 实时估值成功",
            convertMapper.toResponse(valuationService.realtime(accountId))));
  }

  @GetMapping("/{accountId}/valuation/reference")
  public ResponseEntity<ApiResponse<FxPositionValuationResponse>> reference(
      @PathVariable String accountId,
      @RequestParam LocalDate valuationDate,
      @RequestParam(required = false) String rateSourceCode) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取 FX Position 参考估值成功",
            convertMapper.toResponse(
                valuationService.reference(accountId, valuationDate, rateSourceCode))));
  }
}
