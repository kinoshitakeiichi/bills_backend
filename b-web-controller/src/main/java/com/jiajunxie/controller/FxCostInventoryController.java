package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.service.FxCostInventoryService;
import com.jiajunxie.vo.FxCostInventoryValuationVo;
import com.jiajunxie.vo.FxCostInventoryVo;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fx/inventory")
@RequiredArgsConstructor
public class FxCostInventoryController {

  private final FxCostInventoryService service;

  @GetMapping("/{accountId}")
  public ResponseEntity<ApiResponse<FxCostInventoryVo>> inventory(@PathVariable String accountId) {
    return ResponseEntity.ok(ApiResponse.success("FX Cost Inventory 查询成功", service.get(accountId)));
  }

  @GetMapping("/{accountId}/valuation/realtime")
  public ResponseEntity<ApiResponse<FxCostInventoryValuationVo>> realtime(
      @PathVariable String accountId) {
    return ResponseEntity.ok(ApiResponse.success("FX Cost Inventory 实时估值成功", service.realtime(accountId)));
  }

  @GetMapping("/{accountId}/valuation/reference")
  public ResponseEntity<ApiResponse<FxCostInventoryValuationVo>> reference(
      @PathVariable String accountId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate valuationDate,
      @RequestParam(required = false) String rateSourceCode) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "FX Cost Inventory 参考估值成功",
            service.reference(accountId, valuationDate, rateSourceCode)));
  }
}
