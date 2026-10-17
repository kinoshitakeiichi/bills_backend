package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.request.LoanPaymentRequest;
import com.jiajunxie.request.LoanPaymentUpdateRequest;
import com.jiajunxie.request.LoanPaymentAllocationRequest;
import com.jiajunxie.service.LoanPaymentService;
import com.jiajunxie.service.LoanPaymentAllocationService;
import com.jiajunxie.vo.LoanPaymentCreateVo;
import com.jiajunxie.vo.LoanPaymentCreatedVo;
import com.jiajunxie.vo.LoanPaymentUpdateVo;
import com.jiajunxie.domain.loan.LoanPaymentAllocationResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loans/payments")
@RequiredArgsConstructor
public class LoanPaymentController {
  private final LoanPaymentService service;
  private final LoanPaymentAllocationService allocationService;

  @PostMapping
  public ResponseEntity<ApiResponse<LoanPaymentCreatedVo>> create(
      @Valid @RequestBody LoanPaymentRequest request) {
    var command =
        new LoanPaymentCreateVo(
            request.getLoanAccountId(),
            request.getSourceAccountId(),
            request.getScheduleIds(),
            request.getPrincipalAmount(),
            request.getInterestAmount(),
            request.getFeeAmount(),
            request.getCurrency(),
            request.getRemark(),
            request.getTransactionTime());
    return ResponseEntity.ok(ApiResponse.success("贷款还款登记成功", service.create(command)));
  }

  @PutMapping("/{transactionId}")
  public ResponseEntity<ApiResponse<LoanPaymentCreatedVo>> update(
      @PathVariable String transactionId,
      @Valid @RequestBody LoanPaymentUpdateRequest request) {
    var command =
        new LoanPaymentUpdateVo(
            request.getSourceAccountId(),
            request.getRemark(),
            request.getTransactionTime());
    return ResponseEntity.ok(ApiResponse.success("贷款还款修改成功", service.update(transactionId, command)));
  }

  @PostMapping("/{transactionId}/reverse")
  public ResponseEntity<ApiResponse<Integer>> reverse(@PathVariable String transactionId) {
    return ResponseEntity.ok(ApiResponse.success("贷款还款撤销成功", service.reverse(transactionId)));
  }

  @PostMapping("/{transactionId}/allocate")
  public ResponseEntity<ApiResponse<LoanPaymentAllocationResult>> allocateExisting(
      @PathVariable String transactionId,
      @Valid @RequestBody LoanPaymentAllocationRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "贷款还款分配成功",
            allocationService.allocate(transactionId, request.getScheduleIds())));
  }

}
