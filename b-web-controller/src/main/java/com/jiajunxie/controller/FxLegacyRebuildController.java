package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.request.FxLegacyRebuildRequest;
import com.jiajunxie.service.FxLegacyRebuildService;
import com.jiajunxie.vo.FxLegacyRebuildReportVo;
import com.jiajunxie.vo.FxLegacyRebuildRequestVo;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fx/admin/legacy-rebuild")
@RequiredArgsConstructor
public class FxLegacyRebuildController {
  private final FxLegacyRebuildService rebuildService;

  @PostMapping("/preview")
  public ResponseEntity<ApiResponse<List<FxLegacyRebuildReportVo>>> preview(
      @Valid @RequestBody FxLegacyRebuildRequest request) {
    return ResponseEntity.ok(ApiResponse.success("历史 FX 重建预览成功", rebuildService.preview(toVo(request))));
  }

  @PostMapping("/apply")
  public ResponseEntity<ApiResponse<List<FxLegacyRebuildReportVo>>> apply(
      @Valid @RequestBody FxLegacyRebuildRequest request) {
    return ResponseEntity.ok(ApiResponse.success("历史 FX 重建执行成功", rebuildService.apply(toVo(request))));
  }

  private FxLegacyRebuildRequestVo toVo(FxLegacyRebuildRequest request) {
    return new FxLegacyRebuildRequestVo(
        request.getAccountIds(), request.getOpeningBalanceTransactionIds(), request.getOpeningRateLookAheadDays());
  }
}
