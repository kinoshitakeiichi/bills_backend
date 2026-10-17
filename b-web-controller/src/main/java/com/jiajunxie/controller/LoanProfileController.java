package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.convert.LoanProfileConvertWebMapper;
import com.jiajunxie.request.LoanProfileRequest;
import com.jiajunxie.request.LoanRateHistoryRequest;
import com.jiajunxie.response.LoanProfileResponse;
import com.jiajunxie.response.LoanRateHistoryResponse;
import com.jiajunxie.service.LoanProfileService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class LoanProfileController {
  private final LoanProfileService loanProfileService;
  private final LoanProfileConvertWebMapper convertMapper;

  @GetMapping("/{accountId}/profile")
  public ResponseEntity<ApiResponse<LoanProfileResponse>> getProfile(@PathVariable String accountId) {
    var profile = loanProfileService.getProfile(accountId);
    return ResponseEntity.ok(ApiResponse.success("获取贷款配置成功", profile == null ? null : convertMapper.toResponse(profile)));
  }

  @PutMapping("/{accountId}/profile")
  public ResponseEntity<ApiResponse<Integer>> upsertProfile(
      @PathVariable String accountId, @Valid @RequestBody LoanProfileRequest request) {
    return ResponseEntity.ok(ApiResponse.success("贷款配置保存成功", loanProfileService.upsertProfile(accountId, convertMapper.toVo(request))));
  }

  @GetMapping("/{accountId}/rate-history")
  public ResponseEntity<ApiResponse<List<LoanRateHistoryResponse>>> listRateHistory(@PathVariable String accountId) {
    return ResponseEntity.ok(ApiResponse.success("获取贷款利率历史成功", convertMapper.toRateResponses(loanProfileService.listRateHistory(accountId))));
  }

  @PostMapping("/{accountId}/rate-history")
  public ResponseEntity<ApiResponse<Integer>> addRateHistory(
      @PathVariable String accountId, @Valid @RequestBody LoanRateHistoryRequest request) {
    return ResponseEntity.ok(ApiResponse.success("贷款利率历史新增成功", loanProfileService.addRateHistory(accountId, convertMapper.toRateVo(request))));
  }
}
