package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.convert.BusinessCalendarConvertWebMapper;
import com.jiajunxie.request.BusinessCalendarDayRequest;
import com.jiajunxie.request.BusinessCalendarRequest;
import com.jiajunxie.response.BusinessCalendarDayResponse;
import com.jiajunxie.response.BusinessCalendarResponse;
import com.jiajunxie.response.SelectOptionResponse;
import com.jiajunxie.service.BusinessCalendarService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/business-calendars")
@RequiredArgsConstructor
public class BusinessCalendarController {
  private final BusinessCalendarService calendarService;
  private final BusinessCalendarConvertWebMapper convertMapper;

  @GetMapping
  public ResponseEntity<ApiResponse<List<BusinessCalendarResponse>>> list(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Boolean enabled) {
    return ResponseEntity.ok(ApiResponse.success("获取营业日日历成功", convertMapper.toResponses(calendarService.list(keyword, enabled))));
  }

  @GetMapping("/options")
  public ResponseEntity<ApiResponse<List<SelectOptionResponse>>> options() {
    return ResponseEntity.ok(ApiResponse.success("获取营业日日历选项成功", convertMapper.toOptionResponses(calendarService.options())));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<Integer>> create(@Valid @RequestBody BusinessCalendarRequest request) {
    return ResponseEntity.ok(ApiResponse.success("营业日日历创建成功", calendarService.create(convertMapper.toVo(request))));
  }

  @PatchMapping("/{calendarId}")
  public ResponseEntity<ApiResponse<Integer>> update(@PathVariable String calendarId, @Valid @RequestBody BusinessCalendarRequest request) {
    return ResponseEntity.ok(ApiResponse.success("营业日日历更新成功", calendarService.update(calendarId, convertMapper.toVo(request))));
  }

  @PatchMapping("/{calendarId}/enabled")
  public ResponseEntity<ApiResponse<Integer>> setEnabled(@PathVariable String calendarId, @RequestParam boolean enabled) {
    return ResponseEntity.ok(ApiResponse.success(enabled ? "营业日日历启用成功" : "营业日日历停用成功", calendarService.setEnabled(calendarId, enabled)));
  }

  @GetMapping("/{calendarId}/days")
  public ResponseEntity<ApiResponse<List<BusinessCalendarDayResponse>>> listDays(
      @PathVariable String calendarId,
      @RequestParam(required = false) LocalDate dateFrom,
      @RequestParam(required = false) LocalDate dateTo) {
    return ResponseEntity.ok(ApiResponse.success("获取营业日覆盖成功", convertMapper.toDayResponses(calendarService.listDays(calendarId, dateFrom, dateTo))));
  }

  @PutMapping("/{calendarId}/days/{date}")
  public ResponseEntity<ApiResponse<Integer>> upsertDay(
      @PathVariable String calendarId,
      @PathVariable LocalDate date,
      @Valid @RequestBody BusinessCalendarDayRequest request) {
    return ResponseEntity.ok(ApiResponse.success("营业日覆盖保存成功", calendarService.upsertDay(calendarId, date, convertMapper.toDayVo(request))));
  }

  @DeleteMapping("/{calendarId}/days/{date}")
  public ResponseEntity<ApiResponse<Integer>> removeDay(@PathVariable String calendarId, @PathVariable LocalDate date) {
    return ResponseEntity.ok(ApiResponse.success("营业日覆盖删除成功", calendarService.removeDay(calendarId, date)));
  }
}
