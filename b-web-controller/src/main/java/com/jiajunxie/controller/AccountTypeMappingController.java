package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.convert.AccountTypeMappingConvertWebMapper;
import com.jiajunxie.request.AccountTypeMappingRequest;
import com.jiajunxie.response.AccountTypeMappingResponse;
import com.jiajunxie.response.AccountTypeOptionResponse;
import com.jiajunxie.service.AccountTypeMappingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account-type-mappings")
@RequiredArgsConstructor
public class AccountTypeMappingController {

  private final AccountTypeMappingService mappingService;
  private final AccountTypeMappingConvertWebMapper mappingConvertMapper;

  @GetMapping
  public ResponseEntity<ApiResponse<List<AccountTypeMappingResponse>>> listMappings() {
    return ResponseEntity.ok(ApiResponse.success(
        "获取账户类型映射成功",
        mappingConvertMapper.toResponses(mappingService.listMappings())));
  }

  @GetMapping("/options")
  public ResponseEntity<ApiResponse<List<AccountTypeOptionResponse>>> listOptions(
      @RequestParam(required = false) String keyword) {
    return ResponseEntity.ok(ApiResponse.success(
        "获取账户类型选项成功",
        mappingConvertMapper.toOptionResponses(mappingService.listOptions(keyword))));
  }

  @PutMapping("/{accountTypeCode}")
  public ResponseEntity<ApiResponse<Integer>> upsertMapping(
      @PathVariable String accountTypeCode,
      @Valid @RequestBody AccountTypeMappingRequest request) {
    return ResponseEntity.ok(ApiResponse.success(
        "账户类型映射保存成功",
        mappingService.upsertMapping(accountTypeCode, request.getAccountClassCode())));
  }
}
