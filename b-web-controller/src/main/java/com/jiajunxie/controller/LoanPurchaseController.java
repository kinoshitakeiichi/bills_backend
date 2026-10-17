package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.request.LoanPurchaseRequest;
import com.jiajunxie.request.LoanPurchaseAdoptRequest;
import com.jiajunxie.service.LoanPurchaseService;
import com.jiajunxie.vo.LoanPurchaseCreateVo;
import com.jiajunxie.vo.LoanPurchaseCreatedVo;
import com.jiajunxie.vo.LoanPurchaseAdoptedVo;
import com.jiajunxie.vo.AllocationVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loans/purchases")
@RequiredArgsConstructor
public class LoanPurchaseController {
  private final LoanPurchaseService service;
  @PostMapping
  public ResponseEntity<ApiResponse<LoanPurchaseCreatedVo>> create(@Valid @RequestBody LoanPurchaseRequest r) {
    var allocations = r.getAllocations() == null
        ? java.util.List.<AllocationVo>of()
        : r.getAllocations().stream()
            .map(item -> new AllocationVo(
                item.getPersonId(),
                item.getAllocatedAmount(),
                item.getCurrency(),
                item.getAllocationRatio()))
            .toList();
    return ResponseEntity.ok(ApiResponse.success("融资消费登记成功", service.create(new LoanPurchaseCreateVo(
        r.getLoanAccountId(),
        r.getCategoryId(),
        r.getAmount(),
        r.getCurrency(),
        r.getMerchant(),
        r.getRemark(),
        r.getTransactionTime(),
        allocations))));
  }
  @PostMapping("/{transactionId}/adopt")
  public ResponseEntity<ApiResponse<LoanPurchaseAdoptedVo>> adopt(
      @PathVariable String transactionId,
      @Valid @RequestBody LoanPurchaseAdoptRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "历史融资事实已由 Loan Engine 接管",
            service.adoptLegacyPurchase(transactionId, request.getLoanAccountId())));
  }

  @GetMapping("/{loanAccountId}/exists")
  public ResponseEntity<ApiResponse<Boolean>> exists(@PathVariable String loanAccountId) {
    return ResponseEntity.ok(ApiResponse.success("融资消费状态查询成功", service.hasPurchase(loanAccountId)));
  }
@DeleteMapping("/{transactionId}")
public ResponseEntity<ApiResponse<Integer>> remove(@PathVariable String transactionId) {
  return ResponseEntity.ok(ApiResponse.success("未还款融资消费撤销成功", service.remove(transactionId)));
}

  @PutMapping("/{transactionId}")
  public ResponseEntity<ApiResponse<LoanPurchaseCreatedVo>> update(
      @PathVariable String transactionId, @Valid @RequestBody LoanPurchaseRequest r) {
    var allocations = r.getAllocations() == null ? java.util.List.<com.jiajunxie.vo.AllocationVo>of() : r.getAllocations().stream()
        .map(a -> new com.jiajunxie.vo.AllocationVo(
            a.getPersonId(), a.getAllocatedAmount(), a.getCurrency(), a.getAllocationRatio()))
        .toList();
    return ResponseEntity.ok(ApiResponse.success("融资消费修改成功", service.update(transactionId, new LoanPurchaseCreateVo(
        r.getLoanAccountId(), r.getCategoryId(), r.getAmount(), r.getCurrency(), r.getMerchant(), r.getRemark(), r.getTransactionTime(), allocations))));
  }

}
