package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.request.CurrencyInventoryRebuildApplyRequest;
import com.jiajunxie.request.CurrencyInventoryRebuildRequest;
import com.jiajunxie.service.CurrencyInventoryRebuildService;
import com.jiajunxie.vo.CurrencyInventoryRebuildApplyRequestVo;
import com.jiajunxie.vo.CurrencyInventoryRebuildApplyResultVo;
import com.jiajunxie.vo.CurrencyInventoryRebuildPreviewVo;
import com.jiajunxie.vo.CurrencyInventoryRebuildRequestVo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fx/admin/inventory-rebuild")
@RequiredArgsConstructor
public class CurrencyInventoryRebuildController {
  private final CurrencyInventoryRebuildService rebuildService;

  @PostMapping("/preview")
  public ResponseEntity<ApiResponse<CurrencyInventoryRebuildPreviewVo>> preview(
      @RequestBody(required = false) CurrencyInventoryRebuildRequest request) {
    List<String> accountIds = request == null ? List.of() : request.getAccountIds();
    return ResponseEntity.ok(
        ApiResponse.success(
            "FX Cost Inventory V3 当前持仓成本重建预览成功",
            rebuildService.preview(new CurrencyInventoryRebuildRequestVo(accountIds))));
  }

  @PostMapping("/apply")
  public ResponseEntity<ApiResponse<CurrencyInventoryRebuildApplyResultVo>> apply(
      @RequestBody CurrencyInventoryRebuildApplyRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "FX Cost Inventory V3 cutover snapshot persisted",
            rebuildService.apply(
                new CurrencyInventoryRebuildApplyRequestVo(
                    request.getExpectedFingerprint(), request.getConfirmation()))));
  }
}
