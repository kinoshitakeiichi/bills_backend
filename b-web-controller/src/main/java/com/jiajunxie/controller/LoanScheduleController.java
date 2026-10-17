package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.domain.loan.LoanRecastExecutionCommand;
import com.jiajunxie.domain.loan.LoanRecastExecutionResult;
import com.jiajunxie.domain.loan.LoanRecastPreviewCommand;
import com.jiajunxie.domain.loan.LoanRecastPreviewResult;
import com.jiajunxie.domain.loan.LoanRecastStrategy;
import com.jiajunxie.domain.loan.LoanScheduleAmountAdjustmentCommand;
import com.jiajunxie.domain.loan.LoanScheduleAmountField;
import com.jiajunxie.domain.loan.LoanScheduleDateAdjustmentCommand;
import com.jiajunxie.domain.loan.LoanSchedulePlanView;
import com.jiajunxie.domain.loan.LoanScheduleGenerationCommand;
import com.jiajunxie.domain.loan.LoanScheduleGenerationResult;
import com.jiajunxie.domain.loan.LoanScheduleGenerationPreviewResult;
import com.jiajunxie.domain.loan.LoanManualInstallmentInput;
import com.jiajunxie.domain.loan.LoanRepaymentType;
import com.jiajunxie.domain.loan.LoanRemainderRule;
import com.jiajunxie.domain.loan.LoanScheduleAdjustmentMode;
import com.jiajunxie.domain.loan.LoanUnplannedFundingFact;
import com.jiajunxie.service.LoanRecastExecutionService;
import com.jiajunxie.service.LoanRecastPreviewService;
import com.jiajunxie.service.LoanScheduleAdjustmentService;
import com.jiajunxie.service.LoanScheduleQueryService;
import com.jiajunxie.service.LoanScheduleGenerationService;
import com.jiajunxie.service.LoanScheduleFundingQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class LoanScheduleController {
  private final LoanScheduleQueryService queryService;
  private final LoanScheduleAdjustmentService adjustmentService;
  private final LoanRecastPreviewService previewService;
  private final LoanRecastExecutionService executionService;
  private final LoanScheduleGenerationService generationService;
  private final LoanScheduleFundingQueryService fundingQueryService;

  @GetMapping("/{accountId}/schedules")
  public ResponseEntity<ApiResponse<List<LoanSchedulePlanView>>> list(
      @PathVariable String accountId,
      @RequestParam(required = false) LocalDate asOfDate) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取贷款还款计划成功",
            queryService.listByLoanAccount(
                accountId, asOfDate == null ? LocalDate.now() : asOfDate)));
  }

  @GetMapping("/{accountId}/unplanned-funding")
  public ResponseEntity<ApiResponse<List<LoanUnplannedFundingFact>>> unplannedFunding(
      @PathVariable String accountId) {
    return ResponseEntity.ok(
        ApiResponse.success("获取未生成计划的融资事实成功", fundingQueryService.listUnplanned(accountId)));
  }

  @PostMapping("/{accountId}/schedules/preview")
  public ResponseEntity<ApiResponse<LoanScheduleGenerationPreviewResult>> previewGeneration(
      @PathVariable String accountId, @Valid @RequestBody GenerateScheduleRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "贷款还款计划预览成功",
            generationService.preview(toGenerationCommand(accountId, request, false))));
  }

  @PostMapping("/{accountId}/schedules/generate")
  public ResponseEntity<ApiResponse<LoanScheduleGenerationResult>> generate(
      @PathVariable String accountId, @Valid @RequestBody GenerateScheduleRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "贷款还款计划生成成功",
            generationService.generate(toGenerationCommand(accountId, request, true))));
  }

  private LoanScheduleGenerationCommand toGenerationCommand(
      String accountId, GenerateScheduleRequest request, boolean confirm) {
    List<LoanManualInstallmentInput> manual =
        request.getInstallments() == null
            ? List.of()
            : request.getInstallments().stream()
                .map(row -> new LoanManualInstallmentInput(
                    row.getInstallmentNo(), row.getPrincipalAmount(), row.getInterestAmount(), row.getFeeAmount(), row.getPaymentDate()))
                .toList();
    LoanRepaymentType repaymentType = confirm && !manual.isEmpty() ? LoanRepaymentType.MANUAL : request.getRepaymentType();
    return new LoanScheduleGenerationCommand(
        accountId,
        request.getSourceTransactionId(),
        repaymentType,
        request.getInstallmentCount(),
        request.getAmountScale(),
        request.getFirstScheduledPaymentDate(),
        LoanRemainderRule.ROUND_TO_LAST,
        LoanScheduleAdjustmentMode.ADJUST_LAST,
        manual);
  }

@DeleteMapping("/plans/{planId}")
public ResponseEntity<ApiResponse<Integer>> removePlan(@PathVariable String planId) {
  return ResponseEntity.ok(
      ApiResponse.success("未还款贷款计划删除成功", generationService.removePlan(planId)));
}

  @PutMapping("/schedules/{scheduleId}/amount")
  public ResponseEntity<ApiResponse<Void>> adjustAmount(
      @PathVariable String scheduleId, @Valid @RequestBody AmountAdjustmentRequest request) {
    adjustmentService.adjustAmount(
        new LoanScheduleAmountAdjustmentCommand(
            scheduleId, request.getField(), request.getAmount(), request.getReason()));
    return ResponseEntity.ok(ApiResponse.success("还款计划金额调整成功", null));
  }

  @PutMapping("/schedules/{scheduleId}/date")
  public ResponseEntity<ApiResponse<Void>> adjustDate(
      @PathVariable String scheduleId, @Valid @RequestBody DateAdjustmentRequest request) {
    adjustmentService.adjustDate(
        new LoanScheduleDateAdjustmentCommand(
            scheduleId, request.getPaymentDate(), request.getReason()));
    return ResponseEntity.ok(ApiResponse.success("还款日期调整成功", null));
  }

  @PostMapping("/plans/{planId}/recast/preview")
  public ResponseEntity<ApiResponse<LoanRecastPreviewResult>> preview(
      @PathVariable String planId, @Valid @RequestBody RecastRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "贷款重算预览成功",
            previewService.preview(
                new LoanRecastPreviewCommand(
                    planId,
                    request.getFromInstallmentNo(),
                    request.getRemainingPrincipal(),
                    request.getStrategy()))));
  }

  @PostMapping("/plans/{planId}/recast")
  public ResponseEntity<ApiResponse<LoanRecastExecutionResult>> execute(
      @PathVariable String planId, @Valid @RequestBody RecastRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "贷款重算执行成功",
            executionService.execute(
                new LoanRecastExecutionCommand(
                    planId,
                    request.getFromInstallmentNo(),
                    request.getRemainingPrincipal(),
                    request.getStrategy(),
                    request.getReason()))));
  }

  @Data
  public static class GenerateScheduleRequest {
    @NotBlank private String sourceTransactionId;
    @NotNull private LoanRepaymentType repaymentType;
    @Min(1) private int installmentCount;
    @Min(0) private int amountScale;
    @NotNull private LocalDate firstScheduledPaymentDate;
    private List<GenerateInstallmentRequest> installments;
  }

  @Data
  public static class GenerateInstallmentRequest {
    @Min(1) private int installmentNo;
    @NotNull @DecimalMin("0") private BigDecimal principalAmount;
    @NotNull @DecimalMin("0") private BigDecimal interestAmount;
    @NotNull @DecimalMin("0") private BigDecimal feeAmount;
    @NotNull private LocalDate paymentDate;
  }

  @Data
  public static class AmountAdjustmentRequest {
    @NotNull private LoanScheduleAmountField field;
    @NotNull @DecimalMin("0") private BigDecimal amount;
    @NotBlank private String reason;
  }

  @Data
  public static class DateAdjustmentRequest {
    @NotNull private LocalDate paymentDate;
    @NotBlank private String reason;
  }

  @Data
  public static class RecastRequest {
    @Min(1) private int fromInstallmentNo;
    @NotNull @DecimalMin(value = "0", inclusive = false) private BigDecimal remainingPrincipal;
    @NotNull private LoanRecastStrategy strategy;
    private String reason;
  }
}
