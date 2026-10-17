package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.service.StatementPaymentAllocationService;
import com.jiajunxie.vo.StatementPaymentAllocationVo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class StatementPaymentAllocationController {

  private final StatementPaymentAllocationService service;

  @GetMapping("/{transactionId}/statement-payment-allocations")
  public ResponseEntity<ApiResponse<List<StatementPaymentAllocationVo>>> list(
      @PathVariable String transactionId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取信用卡还款账单分配成功",
            service.listByPaymentTransactionId(transactionId)));
  }
}
