package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.convert.DictionaryConvertWebMapper;
import com.jiajunxie.convert.PageConvertWebMapper;
import com.jiajunxie.request.DictionaryItemRequest;
import com.jiajunxie.request.DictionaryTypeRequest;
import com.jiajunxie.request.MasterDataPageRequest;
import com.jiajunxie.response.DictionaryItemResponse;
import com.jiajunxie.response.DictionaryOptionResponse;
import com.jiajunxie.response.DictionaryTypeResponse;
import com.jiajunxie.response.PageResponse;
import com.jiajunxie.service.DictionaryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dictionaries")
@RequiredArgsConstructor
public class DictionaryController {

  private final DictionaryService dictionaryService;
  private final DictionaryConvertWebMapper dictionaryConvertMapper;
  private final PageConvertWebMapper pageConvertMapper;

  @GetMapping
  public ResponseEntity<ApiResponse<List<DictionaryTypeResponse>>> listTypes(
      @RequestParam(required = false) Boolean enabled) {
    return ResponseEntity.ok(ApiResponse.success(
        "获取字典类型成功",
        dictionaryConvertMapper.toTypeResponses(dictionaryService.listTypes(enabled))));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<Integer>> createType(
      @Valid @RequestBody DictionaryTypeRequest request) {
    return ResponseEntity.ok(ApiResponse.success(
        "字典类型创建成功",
        dictionaryService.createType(dictionaryConvertMapper.toTypeVo(request))));
  }

  @PatchMapping("/{typeCode}")
  public ResponseEntity<ApiResponse<Integer>> updateType(
      @PathVariable String typeCode,
      @Valid @RequestBody DictionaryTypeRequest request) {
    return ResponseEntity.ok(ApiResponse.success(
        "字典类型更新成功",
        dictionaryService.updateType(typeCode, dictionaryConvertMapper.toTypeVo(request))));
  }

  @PatchMapping("/{typeCode}/enabled")
  public ResponseEntity<ApiResponse<Integer>> setTypeEnabled(
      @PathVariable String typeCode, @RequestParam boolean enabled) {
    return ResponseEntity.ok(ApiResponse.success(
        enabled ? "字典类型启用成功" : "字典类型停用成功",
        dictionaryService.setTypeEnabled(typeCode, enabled)));
  }

  @GetMapping("/{typeCode}/items")
  public ResponseEntity<ApiResponse<List<DictionaryItemResponse>>> listItems(
      @PathVariable String typeCode,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Boolean enabled) {
    return ResponseEntity.ok(ApiResponse.success(
        "获取字典项成功",
        dictionaryConvertMapper.toItemResponses(
            dictionaryService.listItems(typeCode, keyword, enabled))));
  }

  @GetMapping("/{typeCode}/items/page")
  public ResponseEntity<ApiResponse<PageResponse<DictionaryItemResponse>>> pageItems(
      @PathVariable String typeCode,
      @Valid @ModelAttribute MasterDataPageRequest request,
      @RequestParam(required = false) Boolean enabled) {
    var result = dictionaryService.pageItems(typeCode, pageConvertMapper.toQuery(request), enabled);
    var response = new PageResponse<>(
        result.total(), result.page(), result.size(),
        dictionaryConvertMapper.toItemResponses(result.records()));
    return ResponseEntity.ok(ApiResponse.success("获取字典项分页成功", response));
  }

  @PostMapping("/{typeCode}/items")
  public ResponseEntity<ApiResponse<Integer>> createItem(
      @PathVariable String typeCode,
      @Valid @RequestBody DictionaryItemRequest request) {
    return ResponseEntity.ok(ApiResponse.success(
        "字典项创建成功",
        dictionaryService.createItem(typeCode, dictionaryConvertMapper.toItemVo(request))));
  }

  @PatchMapping("/{typeCode}/items/{dictionaryItemId}")
  public ResponseEntity<ApiResponse<Integer>> updateItem(
      @PathVariable String typeCode,
      @PathVariable String dictionaryItemId,
      @Valid @RequestBody DictionaryItemRequest request) {
    return ResponseEntity.ok(ApiResponse.success(
        "字典项更新成功",
        dictionaryService.updateItem(
            typeCode, dictionaryItemId, dictionaryConvertMapper.toItemVo(request))));
  }

  @PatchMapping("/{typeCode}/items/{dictionaryItemId}/enabled")
  public ResponseEntity<ApiResponse<Integer>> setItemEnabled(
      @PathVariable String typeCode,
      @PathVariable String dictionaryItemId,
      @RequestParam boolean enabled) {
    return ResponseEntity.ok(ApiResponse.success(
        enabled ? "字典项启用成功" : "字典项停用成功",
        dictionaryService.setItemEnabled(typeCode, dictionaryItemId, enabled)));
  }

  @DeleteMapping("/{typeCode}/items/{dictionaryItemId}")
  public ResponseEntity<ApiResponse<Integer>> removeItem(
      @PathVariable String typeCode, @PathVariable String dictionaryItemId) {
    return ResponseEntity.ok(ApiResponse.success(
        "字典项删除成功",
        dictionaryService.removeItem(typeCode, dictionaryItemId)));
  }

  @GetMapping("/{typeCode}/options")
  public ResponseEntity<ApiResponse<List<DictionaryOptionResponse>>> listOptions(
      @PathVariable String typeCode,
      @RequestParam(required = false) String keyword) {
    return ResponseEntity.ok(ApiResponse.success(
        "获取字典选项成功",
        dictionaryConvertMapper.toOptionResponses(
            dictionaryService.listOptions(typeCode, keyword))));
  }
}
