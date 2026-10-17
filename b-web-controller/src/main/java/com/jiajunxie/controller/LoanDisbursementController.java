package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.request.LoanDisbursementRequest;
import com.jiajunxie.service.LoanDisbursementService;
import com.jiajunxie.vo.LoanDisbursementCreateVo;
import com.jiajunxie.vo.LoanDisbursementCreatedVo;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/loans/disbursements")
@RequiredArgsConstructor
public class LoanDisbursementController {
  private final LoanDisbursementService service;

  @GetMapping("/{loanAccountId}/exists")
  public ResponseEntity<ApiResponse<Boolean>> exists(@PathVariable String loanAccountId) {
    return ResponseEntity.ok(
        ApiResponse.success("贷款放款状态查询成功", service.hasDisbursement(loanAccountId)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<LoanDisbursementCreatedVo>> create(
      @Valid @RequestBody LoanDisbursementRequest request) {
    var command = new LoanDisbursementCreateVo(
        request.getLoanAccountId(),
        request.getTargetAccountId(),
        request.getAmount(),
        request.getCurrency(),
        request.getRemark(),
        request.getTransactionTime());
    return ResponseEntity.ok(ApiResponse.success("贷款放款登记成功", service.create(command)));
  }
@DeleteMapping("/{transactionId}")
public ResponseEntity<ApiResponse<Integer>> remove(@PathVariable String transactionId) {
  return ResponseEntity.ok(ApiResponse.success("未还款贷款放款撤销成功", service.remove(transactionId)));
}

  @PutMapping("/{transactionId}")
  public ResponseEntity<ApiResponse<LoanDisbursementCreatedVo>> update(
      @PathVariable String transactionId, @Valid @RequestBody LoanDisbursementRequest request) {
    var command = new LoanDisbursementCreateVo(
        request.getLoanAccountId(),
        request.getTargetAccountId(),
        request.getAmount(),
        request.getCurrency(),
        request.getRemark(),
        request.getTransactionTime());
    return ResponseEntity.ok(ApiResponse.success("贷款放款修改成功", service.update(transactionId, command)));
  }

}
