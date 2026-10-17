package com.jiajunxie.controller;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.convert.BudgetConvertWebMapper;
import com.jiajunxie.request.BudgetRequest;
import com.jiajunxie.response.BudgetActualResponse;
import com.jiajunxie.response.BudgetResponse;
import com.jiajunxie.service.BudgetService;
import jakarta.validation.Valid;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
public class BudgetController {
  private final BudgetService budgetService;
  private final BudgetConvertWebMapper budgetConvertMapper;

  @GetMapping
  public ResponseEntity<ApiResponse<List<BudgetResponse>>> list() {
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取预算成功",
            budgetConvertMapper.toResponses(budgetService.listBudgets())));
  }

  @GetMapping("/{budgetId}")
  public ResponseEntity<ApiResponse<BudgetResponse>> detail(@PathVariable String budgetId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取预算详情成功",
            budgetConvertMapper.toResponse(budgetService.getBudget(budgetId))));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<String>> create(
      @Valid @RequestBody BudgetRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "预算创建成功",
            budgetService.createBudget(budgetConvertMapper.toSaveVo(request))));
  }

  @PatchMapping("/{budgetId}")
  public ResponseEntity<ApiResponse<Integer>> update(
      @PathVariable String budgetId,
      @Valid @RequestBody BudgetRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "预算更新成功",
            budgetService.updateBudget(budgetId, budgetConvertMapper.toSaveVo(request))));
  }

  @DeleteMapping("/{budgetId}")
  public ResponseEntity<ApiResponse<Integer>> remove(@PathVariable String budgetId) {
    return ResponseEntity.ok(
        ApiResponse.success("预算删除成功", budgetService.removeBudget(budgetId)));
  }

  @GetMapping("/{budgetId}/actual")
  public ResponseEntity<ApiResponse<BudgetActualResponse>> actual(
      @PathVariable String budgetId,
      @RequestParam(defaultValue = "Asia/Tokyo") String timezone) {
    ZoneId zoneId = parseTimezone(timezone);
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取预算执行情况成功",
            budgetConvertMapper.toActualResponse(budgetService.getActual(budgetId, zoneId))));
  }

  private ZoneId parseTimezone(String timezone) {
    try {
      return ZoneId.of(timezone);
    } catch (DateTimeException exception) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "无效的时区：" + timezone);
    }
  }
}
