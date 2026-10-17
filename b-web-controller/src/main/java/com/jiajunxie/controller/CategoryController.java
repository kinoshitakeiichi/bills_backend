package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.convert.CategoryConvertWebMapper;
import com.jiajunxie.convert.PageConvertWebMapper;
import com.jiajunxie.request.CategoryRequest;
import com.jiajunxie.request.MasterDataPageRequest;
import com.jiajunxie.response.CategoryManagementTreeResponse;
import com.jiajunxie.response.CategoryResponse;
import com.jiajunxie.response.CategoryTreeResponse;
import com.jiajunxie.response.PageResponse;
import com.jiajunxie.response.SelectOptionResponse;
import com.jiajunxie.service.CategoryService;
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
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {
  private final CategoryService categoryService;
  private final CategoryConvertWebMapper categoryConvertMapper;
  private final PageConvertWebMapper pageConvertMapper;

  @GetMapping
  public ResponseEntity<ApiResponse<List<CategoryResponse>>> list() {
    return ResponseEntity.ok(ApiResponse.success(
        "获取分类成功",
        categoryConvertMapper.toResponses(categoryService.listCategories())));
  }

  @GetMapping("/page")
  public ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> page(
      @Valid @ModelAttribute MasterDataPageRequest request,
      @RequestParam(required = false) String typeCode,
      @RequestParam(required = false) Boolean enabled) {
    var result = categoryService.pageCategories(pageConvertMapper.toQuery(request), typeCode, enabled);
    var response = new PageResponse<>(
        result.total(), result.page(), result.size(),
        categoryConvertMapper.toResponses(result.records()));
    return ResponseEntity.ok(ApiResponse.success("获取分类分页成功", response));
  }

  @GetMapping("/options")
  public ResponseEntity<ApiResponse<List<SelectOptionResponse>>> options(
      @RequestParam(required = false) String typeCode) {
    return ResponseEntity.ok(ApiResponse.success(
        "获取分类选项成功",
        categoryConvertMapper.toOptionResponses(categoryService.listOptions(typeCode))));
  }

  /**
   * Enabled business tree used by transaction forms. Disabled nodes and descendants under disabled
   * ancestors are intentionally omitted from this endpoint.
   */
  @GetMapping("/tree")
  public ResponseEntity<ApiResponse<List<CategoryTreeResponse>>> tree(
      @RequestParam(required = false) String typeCode) {
    return ResponseEntity.ok(ApiResponse.success(
        "获取分类树成功",
        categoryConvertMapper.toTreeResponses(categoryService.listTree(typeCode))));
  }

  /** Complete tree for the master-data maintenance screen, including disabled nodes. */
  @GetMapping("/management-tree")
  public ResponseEntity<ApiResponse<List<CategoryManagementTreeResponse>>> managementTree(
      @RequestParam(required = false) String typeCode) {
    return ResponseEntity.ok(ApiResponse.success(
        "获取分类维护树成功",
        categoryConvertMapper.toManagementTreeResponses(categoryService.listManagementTree(typeCode))));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<Integer>> create(@Valid @RequestBody CategoryRequest request) {
    return ResponseEntity.ok(ApiResponse.success(
        "分类创建成功",
        categoryService.createCategory(categoryConvertMapper.toVo(request))));
  }

  @PatchMapping("/{categoryId}")
  public ResponseEntity<ApiResponse<Integer>> update(
      @PathVariable String categoryId,
      @Valid @RequestBody CategoryRequest request) {
    return ResponseEntity.ok(ApiResponse.success(
        "分类更新成功",
        categoryService.updateCategory(categoryId, categoryConvertMapper.toVo(request))));
  }

  @PatchMapping("/{categoryId}/enabled")
  public ResponseEntity<ApiResponse<Integer>> setEnabled(
      @PathVariable String categoryId,
      @RequestParam boolean enabled) {
    return ResponseEntity.ok(ApiResponse.success(
        enabled ? "分类启用成功" : "分类停用成功",
        categoryService.setEnabled(categoryId, enabled)));
  }

  @DeleteMapping("/{categoryId}")
  public ResponseEntity<ApiResponse<Integer>> remove(@PathVariable String categoryId) {
    return ResponseEntity.ok(ApiResponse.success(
        "分类删除成功",
        categoryService.removeCategory(categoryId)));
  }
}
