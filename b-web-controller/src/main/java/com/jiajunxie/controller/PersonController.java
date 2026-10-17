package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.convert.PageConvertWebMapper;
import com.jiajunxie.convert.PersonConvertWebMapper;
import com.jiajunxie.request.MasterDataPageRequest;
import com.jiajunxie.request.PersonRequest;
import com.jiajunxie.response.PageResponse;
import com.jiajunxie.response.PersonResponse;
import com.jiajunxie.response.SelectOptionResponse;
import com.jiajunxie.service.PersonService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/persons")
@RequiredArgsConstructor
public class PersonController {
  private final PersonService personService;
  private final PersonConvertWebMapper personConvertMapper;
  private final PageConvertWebMapper pageConvertMapper;

  @GetMapping
  public ResponseEntity<ApiResponse<List<PersonResponse>>> list() {
    return ResponseEntity.ok(ApiResponse.success(
        "获取人员成功",
        personConvertMapper.toResponses(personService.listPersons())));
  }

  @GetMapping("/page")
  public ResponseEntity<ApiResponse<PageResponse<PersonResponse>>> page(
      @Valid @ModelAttribute MasterDataPageRequest request,
      @org.springframework.web.bind.annotation.RequestParam(required = false) Boolean enabled) {
    var result = personService.pagePersons(pageConvertMapper.toQuery(request), enabled);
    var response = new PageResponse<>(
        result.total(), result.page(), result.size(),
        personConvertMapper.toResponses(result.records()));
    return ResponseEntity.ok(ApiResponse.success("获取人员分页成功", response));
  }

  @GetMapping("/options")
  public ResponseEntity<ApiResponse<List<SelectOptionResponse>>> options() {
    return ResponseEntity.ok(ApiResponse.success(
        "获取人员选项成功",
        personConvertMapper.toOptionResponses(personService.listOptions())));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<Integer>> create(@Valid @RequestBody PersonRequest request) {
    return ResponseEntity.ok(ApiResponse.success(
        "人员创建成功",
        personService.createPerson(personConvertMapper.toVo(request))));
  }

  @PatchMapping("/{personId}")
  public ResponseEntity<ApiResponse<Integer>> update(
      @PathVariable String personId,
      @Valid @RequestBody PersonRequest request) {
    return ResponseEntity.ok(ApiResponse.success(
        "人员更新成功",
        personService.updatePerson(personId, personConvertMapper.toVo(request))));
  }

  @PatchMapping("/{personId}/enabled")
  public ResponseEntity<ApiResponse<Integer>> setEnabled(
      @PathVariable String personId, @RequestParam boolean enabled) {
    return ResponseEntity.ok(ApiResponse.success(
        enabled ? "人员启用成功" : "人员停用成功",
        personService.setEnabled(personId, enabled)));
  }
}
