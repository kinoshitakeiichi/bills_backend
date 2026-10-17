package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.request.ReminderRequest;
import com.jiajunxie.response.ReminderDatePreviewResponse;
import com.jiajunxie.response.ReminderResponse;
import com.jiajunxie.service.ReminderService;
import com.jiajunxie.vo.ReminderDatePreviewVo;
import com.jiajunxie.vo.ReminderVo;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reminders")
@RequiredArgsConstructor
public class ReminderController {

  private final ReminderService reminderService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<ReminderResponse>>> list(
      @RequestParam(required = false) Boolean enabled) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取提醒成功",
            reminderService.list(enabled).stream().map(this::toResponse).toList()));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ReminderResponse>> create(
      @Valid @RequestBody ReminderRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success("提醒创建成功", toResponse(reminderService.create(toVo(request)))));
  }

  @PutMapping("/{reminderId}")
  public ResponseEntity<ApiResponse<ReminderResponse>> update(
      @PathVariable String reminderId,
      @Valid @RequestBody ReminderRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "提醒更新成功", toResponse(reminderService.update(reminderId, toVo(request)))));
  }

  @PatchMapping("/{reminderId}/enabled")
  public ResponseEntity<ApiResponse<Integer>> setEnabled(
      @PathVariable String reminderId, @RequestParam boolean enabled) {
    return ResponseEntity.ok(
        ApiResponse.success(
            enabled ? "提醒启用成功" : "提醒停用成功",
            reminderService.setEnabled(reminderId, enabled)));
  }

  @DeleteMapping("/{reminderId}")
  public ResponseEntity<ApiResponse<Integer>> delete(@PathVariable String reminderId) {
    return ResponseEntity.ok(ApiResponse.success("提醒删除成功", reminderService.delete(reminderId)));
  }

  @PostMapping("/preview")
  public ResponseEntity<ApiResponse<ReminderDatePreviewResponse>> preview(
      @Valid @RequestBody ReminderRequest request,
      @RequestParam(required = false) LocalDate referenceDate) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "提醒日期预览成功",
            toPreviewResponse(reminderService.preview(toVo(request), referenceDate))));
  }

  @PostMapping("/evaluate")
  public ResponseEntity<ApiResponse<Void>> evaluate() {
    reminderService.evaluateDueReminders();
    return ResponseEntity.ok(ApiResponse.success("提醒检查完成", null));
  }

  private ReminderVo toVo(ReminderRequest request) {
    return new ReminderVo(
        null,
        request.reminderName(),
        request.reminderTypeCode(),
        request.accountId(),
        request.scheduleModeCode(),
        request.remindDate(),
        request.dayOfMonth(),
        request.remindTime(),
        request.timezone(),
        request.dateRuleSourceCode(),
        request.invalidDayRuleCode(),
        request.calendarCode(),
        request.businessDayAdjustmentCode(),
        request.notificationUrl(),
        request.notificationTitle(),
        request.notificationBody(),
        request.enabled(),
        null,
        null,
        null);
  }

  private ReminderResponse toResponse(ReminderVo vo) {
    return new ReminderResponse(
        vo.reminderId(),
        vo.reminderName(),
        vo.reminderTypeCode(),
        vo.accountId(),
        vo.scheduleModeCode(),
        vo.remindDate(),
        vo.dayOfMonth(),
        vo.remindTime(),
        vo.timezone(),
        vo.dateRuleSourceCode(),
        vo.invalidDayRuleCode(),
        vo.calendarCode(),
        vo.businessDayAdjustmentCode(),
        vo.notificationUrl(),
        vo.notificationTitle(),
        vo.notificationBody(),
        vo.enabled(),
        vo.lastTriggeredAt(),
        vo.createdAt(),
        vo.updatedAt());
  }

  private ReminderDatePreviewResponse toPreviewResponse(ReminderDatePreviewVo vo) {
    return new ReminderDatePreviewResponse(
        vo.scheduledDate(),
        vo.calculatedDate(),
        vo.dateRuleSourceCode(),
        vo.invalidDayRuleCode(),
        vo.calendarCode(),
        vo.businessDayAdjustmentCode(),
        vo.timezone(),
        vo.sourceDescription());
  }
}
