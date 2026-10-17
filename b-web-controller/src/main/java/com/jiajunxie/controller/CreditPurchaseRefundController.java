package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.request.CreditPurchaseRefundRequest;
import com.jiajunxie.service.CreditPurchaseRefundService;
import com.jiajunxie.vo.CreditPurchaseRefundRequestVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/credit-purchases")
@RequiredArgsConstructor
public class CreditPurchaseRefundController {
  private final CreditPurchaseRefundService refundService;

  @PostMapping("/{transactionId}/refunds")
  public ResponseEntity<ApiResponse<String>> refund(
      @PathVariable String transactionId,
      @Valid @RequestBody CreditPurchaseRefundRequest request) {
    String refundId = refundService.refund(
        transactionId,
        new CreditPurchaseRefundRequestVo(
            request.amount(), request.transactionTime(), request.remark()));
    return ResponseEntity.ok(ApiResponse.success("信用卡消费退款成功", refundId));
  }
  @DeleteMapping("/refunds/{refundTransactionId}")
  public ResponseEntity<ApiResponse<Void>> reverseRefund(
      @PathVariable String refundTransactionId) {
    refundService.reverse(refundTransactionId);
    return ResponseEntity.ok(ApiResponse.success("信用卡消费退款撤销成功", null));
  }

}
