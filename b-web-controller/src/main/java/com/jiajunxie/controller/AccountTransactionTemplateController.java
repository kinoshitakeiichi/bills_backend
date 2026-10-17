package com.jiajunxie.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.request.AccountTransactionTemplateRequest;
import com.jiajunxie.response.AccountTransactionTemplateResponse;
import com.jiajunxie.service.AccountTransactionTemplateService;
import com.jiajunxie.vo.AccountTransactionTemplateSaveVo;
import com.jiajunxie.vo.AccountTransactionTemplateVo;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/transaction-templates")
@RequiredArgsConstructor
public class AccountTransactionTemplateController {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final AccountTransactionTemplateService service;

  @GetMapping
  public ResponseEntity<ApiResponse<List<AccountTransactionTemplateResponse>>> list(@PathVariable String accountId) {
    return ResponseEntity.ok(ApiResponse.success("获取快捷交易模板成功", service.list(accountId).stream().map(this::toResponse).toList()));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<String>> create(@PathVariable String accountId, @Valid @RequestBody AccountTransactionTemplateRequest request) {
    return ResponseEntity.ok(ApiResponse.success("快捷交易模板创建成功", service.create(accountId, toSaveVo(request))));
  }

  @PutMapping("/{templateId}")
  public ResponseEntity<ApiResponse<Integer>> update(@PathVariable String accountId, @PathVariable String templateId,
      @Valid @RequestBody AccountTransactionTemplateRequest request) {
    return ResponseEntity.ok(ApiResponse.success("快捷交易模板更新成功", service.update(accountId, templateId, toSaveVo(request))));
  }

  @DeleteMapping("/{templateId}")
  public ResponseEntity<ApiResponse<Integer>> remove(@PathVariable String accountId, @PathVariable String templateId) {
    return ResponseEntity.ok(ApiResponse.success("快捷交易模板删除成功", service.remove(accountId, templateId)));
  }

  private AccountTransactionTemplateSaveVo toSaveVo(AccountTransactionTemplateRequest request) {
    try {
      return new AccountTransactionTemplateSaveVo(request.templateName(), OBJECT_MAPPER.writeValueAsString(request.preset()), request.sortOrder(), request.enabled());
    } catch (Exception exception) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "交易模板JSON格式不正确");
    }
  }

  private AccountTransactionTemplateResponse toResponse(AccountTransactionTemplateVo vo) {
    try {
      JsonNode preset = OBJECT_MAPPER.readTree(vo.presetJson());
      return new AccountTransactionTemplateResponse(vo.templateId(), vo.accountId(), vo.templateName(), preset, vo.sortOrder(), vo.enabled());
    } catch (Exception exception) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "快捷交易模板数据损坏");
    }
  }
}
