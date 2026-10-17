package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.convert.CreditCardProfileConvertWebMapper;
import com.jiajunxie.request.CreditCardDatePreviewRequest;
import com.jiajunxie.request.CreditCardProfileRequest;
import com.jiajunxie.request.CreditPaymentRuleRequest;
import com.jiajunxie.response.CreditCardDatePreviewResponse;
import com.jiajunxie.response.CreditCardProfileResponse;
import com.jiajunxie.response.CreditPaymentRuleResponse;
import com.jiajunxie.service.CreditCardDateRuleService;
import com.jiajunxie.service.CreditCardProfileService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/credit-cards")
@RequiredArgsConstructor
public class CreditCardProfileController {
  private final CreditCardProfileService profileService;
  private final CreditCardDateRuleService dateRuleService;
  private final CreditCardProfileConvertWebMapper convertMapper;

  @GetMapping("/{accountId}/profile")
  public ResponseEntity<ApiResponse<CreditCardProfileResponse>> getProfile(@PathVariable String accountId) {
    var profile = profileService.getProfile(accountId);
    return ResponseEntity.ok(ApiResponse.success("获取信用卡账单配置成功", profile == null ? null : convertMapper.toResponse(profile)));
  }

  @PutMapping("/{accountId}/profile")
  public ResponseEntity<ApiResponse<Integer>> upsertProfile(
      @PathVariable String accountId, @Valid @RequestBody CreditCardProfileRequest request) {
    return ResponseEntity.ok(ApiResponse.success("信用卡账单配置保存成功", profileService.upsertProfile(accountId, convertMapper.toVo(request))));
  }

  @GetMapping("/{accountId}/payment-rules")
  public ResponseEntity<ApiResponse<List<CreditPaymentRuleResponse>>> listRules(@PathVariable String accountId) {
    return ResponseEntity.ok(ApiResponse.success("获取信用卡特殊支付规则成功", convertMapper.toRuleResponses(profileService.listRules(accountId))));
  }

  @PostMapping("/{accountId}/payment-rules")
  public ResponseEntity<ApiResponse<Integer>> createRule(
      @PathVariable String accountId, @Valid @RequestBody CreditPaymentRuleRequest request) {
    return ResponseEntity.ok(ApiResponse.success("信用卡特殊支付规则创建成功", profileService.createRule(accountId, convertMapper.toRuleVo(request))));
  }

  @PatchMapping("/{accountId}/payment-rules/{ruleId}")
  public ResponseEntity<ApiResponse<Integer>> updateRule(
      @PathVariable String accountId, @PathVariable String ruleId,
      @Valid @RequestBody CreditPaymentRuleRequest request) {
    return ResponseEntity.ok(ApiResponse.success("信用卡特殊支付规则更新成功", profileService.updateRule(accountId, ruleId, convertMapper.toRuleVo(request))));
  }

  @PatchMapping("/{accountId}/payment-rules/{ruleId}/enabled")
  public ResponseEntity<ApiResponse<Integer>> setRuleEnabled(
      @PathVariable String accountId, @PathVariable String ruleId, @RequestParam boolean enabled) {
    return ResponseEntity.ok(ApiResponse.success(enabled ? "特殊支付规则启用成功" : "特殊支付规则停用成功", profileService.setRuleEnabled(accountId, ruleId, enabled)));
  }

  @GetMapping("/{accountId}/date-preview")
  public ResponseEntity<ApiResponse<CreditCardDatePreviewResponse>> previewSaved(
      @PathVariable String accountId, @RequestParam LocalDate referenceDate) {
    return ResponseEntity.ok(ApiResponse.success("信用卡日期规则预览成功", convertMapper.toPreviewResponse(dateRuleService.previewSaved(accountId, referenceDate))));
  }

  @PostMapping("/date-preview")
  public ResponseEntity<ApiResponse<CreditCardDatePreviewResponse>> previewDraft(
      @Valid @RequestBody CreditCardDatePreviewRequest request) {
    var profile = convertMapper.toVo(request.getProfile());
    if (request.getRules() != null) profile.setRules(convertMapper.toRuleVos(request.getRules()));
    return ResponseEntity.ok(ApiResponse.success("信用卡日期规则预览成功", convertMapper.toPreviewResponse(dateRuleService.previewDraft(profile, request.getReferenceDate()))));
  }
}
