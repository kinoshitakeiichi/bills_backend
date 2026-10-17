package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.request.CreditCardStatementGenerateRequest;
import com.jiajunxie.request.CreditCardStatementReopenRequest;
import com.jiajunxie.request.CreditCardStatementUpdateRequest;
import com.jiajunxie.request.StatementAdjustmentRequest;
import com.jiajunxie.service.CreditCardStatementService;
import com.jiajunxie.service.StatementAdjustmentService;
import com.jiajunxie.vo.CreditCardStatementUpdateVo;
import com.jiajunxie.vo.CreditCardStatementVo;
import com.jiajunxie.vo.StatementAdjustmentCreateVo;
import com.jiajunxie.vo.StatementAdjustmentVo;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CreditCardStatementController {

  private final CreditCardStatementService service;
  private final StatementAdjustmentService adjustmentService;

  @GetMapping("/api/v1/credit-cards/{accountId}/statements")
  public ResponseEntity<ApiResponse<List<CreditCardStatementVo>>> list(
      @PathVariable String accountId,
      @RequestParam(required = false) String status) {
    return ResponseEntity.ok(
        ApiResponse.success("获取信用卡账单成功", service.list(accountId, status)));
  }

  @GetMapping("/api/v1/credit-cards/{accountId}/statements/payable")
  public ResponseEntity<ApiResponse<List<CreditCardStatementVo>>> payable(
      @PathVariable String accountId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取可偿还信用卡账单成功", service.listPayable(accountId)));
  }

  @PostMapping("/api/v1/credit-cards/{accountId}/statements/generate")
  public ResponseEntity<ApiResponse<CreditCardStatementVo>> generate(
      @PathVariable String accountId,
      @Valid @RequestBody CreditCardStatementGenerateRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "信用卡账单生成成功",
            service.generate(accountId, request.getReferenceDate())));
  }

  @GetMapping("/api/v1/statements/{statementId}")
  public ResponseEntity<ApiResponse<CreditCardStatementVo>> detail(
      @PathVariable String statementId) {
    return ResponseEntity.ok(
        ApiResponse.success("获取信用卡账单详情成功", service.get(statementId)));
  }

  @PatchMapping("/api/v1/statements/{statementId}")
  public ResponseEntity<ApiResponse<CreditCardStatementVo>> updateOpenDates(
      @PathVariable String statementId,
      @Valid @RequestBody CreditCardStatementUpdateRequest request) {
    CreditCardStatementUpdateVo update =
        new CreditCardStatementUpdateVo(
            request.getPeriodStart(),
            request.getPeriodEnd(),
            request.getConfirmationDate(),
            request.getScheduledPaymentDate(),
            request.getCalculatedPaymentDate(),
            request.getManualPaymentDate());

    return ResponseEntity.ok(
        ApiResponse.success(
            "信用卡账单日期更新成功",
            service.updateOpenDates(statementId, update)));
  }

  @PostMapping("/api/v1/statements/{statementId}/confirm")
  public ResponseEntity<ApiResponse<CreditCardStatementVo>> confirm(
      @PathVariable String statementId) {
    return ResponseEntity.ok(
        ApiResponse.success("信用卡账单确认成功", service.confirm(statementId)));
  }

  @PostMapping("/api/v1/statements/{statementId}/reopen")
  public ResponseEntity<ApiResponse<CreditCardStatementVo>> reopen(
      @PathVariable String statementId,
      @Valid @RequestBody CreditCardStatementReopenRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "信用卡账单撤销确认成功",
            service.reopen(statementId, request.getReason())));
  }

  @PostMapping("/api/v1/statements/{statementId}/cancel")
  public ResponseEntity<ApiResponse<CreditCardStatementVo>> cancel(
      @PathVariable String statementId) {
    return ResponseEntity.ok(
        ApiResponse.success("信用卡账单取消成功", service.cancel(statementId)));
  }

  @GetMapping("/api/v1/statements/{statementId}/adjustments")
  public ResponseEntity<ApiResponse<List<StatementAdjustmentVo>>> adjustments(@PathVariable String statementId) {
    return ResponseEntity.ok(ApiResponse.success("获取账单调整记录成功", adjustmentService.list(statementId)));
  }

  @PostMapping("/api/v1/statements/{statementId}/adjustments")
  public ResponseEntity<ApiResponse<StatementAdjustmentVo>> createAdjustment(
      @PathVariable String statementId, @RequestBody StatementAdjustmentRequest request) {
    StatementAdjustmentCreateVo command = new StatementAdjustmentCreateVo(
        request.getAdjustmentTypeCode(), request.getAmount(), request.getCurrency(),
        request.getOldPaymentDate(), request.getNewPaymentDate(), request.getReason());
    return ResponseEntity.ok(ApiResponse.success("账单调整创建成功", adjustmentService.create(statementId, command)));
  }

  @PostMapping("/api/v1/statements/{statementId}/adjustments/{adjustmentId}/reverse")
  public ResponseEntity<ApiResponse<StatementAdjustmentVo>> reverseAdjustment(
      @PathVariable String statementId, @PathVariable String adjustmentId) {
    return ResponseEntity.ok(ApiResponse.success("账单调整撤销成功", adjustmentService.reverse(statementId, adjustmentId)));
  }

}
