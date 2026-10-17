package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.convert.ExchangeRateAlertConvertWebMapper;
import com.jiajunxie.request.ExchangeRateAlertRuleRequest;
import com.jiajunxie.request.ExchangeRateAlertSettingsRequest;
import com.jiajunxie.response.ExchangeRateAlertRuleResponse;
import com.jiajunxie.response.ExchangeRateAlertSettingsResponse;
import com.jiajunxie.service.ExchangeRateAlertService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exchange-rate-alerts")
@RequiredArgsConstructor
public class ExchangeRateAlertController {

  private final ExchangeRateAlertService alertService;
  private final ExchangeRateAlertConvertWebMapper convertMapper;

  @GetMapping("/settings")
  public ResponseEntity<ApiResponse<ExchangeRateAlertSettingsResponse>> getSettings() {
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取汇率提醒设置成功",
            convertMapper.toSettingsResponse(alertService.getSettings())));
  }

  @PutMapping("/settings")
  public ResponseEntity<ApiResponse<ExchangeRateAlertSettingsResponse>> updateSettings(
      @Valid @RequestBody ExchangeRateAlertSettingsRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "汇率提醒设置保存成功",
            convertMapper.toSettingsResponse(
                alertService.updateSettings(convertMapper.toSettingsVo(request)))));
  }

  @GetMapping("/rules")
  public ResponseEntity<ApiResponse<List<ExchangeRateAlertRuleResponse>>> listRules(
      @RequestParam(required = false) Boolean enabled) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取汇率提醒规则成功",
            convertMapper.toRuleResponses(alertService.listRules(enabled))));
  }

  @PostMapping("/rules")
  public ResponseEntity<ApiResponse<ExchangeRateAlertRuleResponse>> createRule(
      @Valid @RequestBody ExchangeRateAlertRuleRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "汇率提醒规则创建成功",
            convertMapper.toRuleResponse(
                alertService.createRule(convertMapper.toRuleVo(request)))));
  }

  @PutMapping("/rules/{alertRuleId}")
  public ResponseEntity<ApiResponse<ExchangeRateAlertRuleResponse>> updateRule(
      @PathVariable String alertRuleId,
      @Valid @RequestBody ExchangeRateAlertRuleRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "汇率提醒规则更新成功",
            convertMapper.toRuleResponse(
                alertService.updateRule(alertRuleId, convertMapper.toRuleVo(request)))));
  }

  @PatchMapping("/rules/{alertRuleId}/enabled")
  public ResponseEntity<ApiResponse<Integer>> setRuleEnabled(
      @PathVariable String alertRuleId, @RequestParam boolean enabled) {
    return ResponseEntity.ok(
        ApiResponse.success(
            enabled ? "汇率提醒规则启用成功" : "汇率提醒规则停用成功",
            alertService.setRuleEnabled(alertRuleId, enabled)));
  }

  @DeleteMapping("/rules/{alertRuleId}")
  public ResponseEntity<ApiResponse<Integer>> deleteRule(@PathVariable String alertRuleId) {
    return ResponseEntity.ok(
        ApiResponse.success("汇率提醒规则删除成功", alertService.deleteRule(alertRuleId)));
  }

  @PostMapping("/evaluate")
  public ResponseEntity<ApiResponse<Void>> evaluateNow() {
    alertService.evaluateEnabledRules();
    return ResponseEntity.ok(ApiResponse.success("汇率提醒规则评估完成", null));
  }
}
