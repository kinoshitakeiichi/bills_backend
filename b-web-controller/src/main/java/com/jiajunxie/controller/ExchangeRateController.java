package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.convert.ExchangeRateConvertWebMapper;
import com.jiajunxie.request.ExchangeRateUpsertRequest;
import com.jiajunxie.response.ExchangeRateResponse;
import com.jiajunxie.response.RealtimeExchangeRateResponse;
import com.jiajunxie.service.ExchangeRateService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

  private final ExchangeRateService exchangeRateService;
  private final ExchangeRateConvertWebMapper convertMapper;

  @GetMapping
  public ResponseEntity<ApiResponse<List<ExchangeRateResponse>>> list(
      @RequestParam(required = false) String baseCurrency,
      @RequestParam(required = false) String quoteCurrency,
      @RequestParam(required = false) LocalDate dateFrom,
      @RequestParam(required = false) LocalDate dateTo,
      @RequestParam(required = false) String rateSourceCode) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取汇率列表成功",
            convertMapper.toResponses(
                exchangeRateService.list(
                    baseCurrency, quoteCurrency, dateFrom, dateTo, rateSourceCode))));
  }

  @PutMapping("/manual")
  public ResponseEntity<ApiResponse<ExchangeRateResponse>> upsertManual(
      @Valid @RequestBody ExchangeRateUpsertRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "手工汇率保存成功",
            convertMapper.toResponse(
                exchangeRateService.upsertManual(
                    request.getRateDate(),
                    request.getBaseCurrency(),
                    request.getQuoteCurrency(),
                    request.getExchangeRate()))));
  }

  @GetMapping("/exact")
  public ResponseEntity<ApiResponse<ExchangeRateResponse>> exact(
      @RequestParam LocalDate rateDate,
      @RequestParam String baseCurrency,
      @RequestParam String quoteCurrency,
      @RequestParam(required = false) String rateSourceCode) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取指定日期汇率成功",
            convertMapper.toResponse(
                exchangeRateService.exact(
                    rateDate, baseCurrency, quoteCurrency, rateSourceCode))));
  }

  @GetMapping("/effective")
  public ResponseEntity<ApiResponse<ExchangeRateResponse>> effective(
      @RequestParam LocalDate valuationDate,
      @RequestParam String baseCurrency,
      @RequestParam String quoteCurrency,
      @RequestParam(required = false) String rateSourceCode) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取有效汇率成功",
            convertMapper.toResponse(
                exchangeRateService.effective(
                    valuationDate, baseCurrency, quoteCurrency, rateSourceCode))));
  }

  @GetMapping("/realtime")
  public ResponseEntity<ApiResponse<RealtimeExchangeRateResponse>> realtime(
      @RequestParam String baseCurrency, @RequestParam String quoteCurrency) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取 TradingView 实时汇率成功",
            convertMapper.toRealtimeResponse(
                exchangeRateService.realtime(baseCurrency, quoteCurrency))));
  }

  @PostMapping("/tradingview/snapshot")
  public ResponseEntity<ApiResponse<ExchangeRateResponse>> snapshotTradingView(
      @RequestParam(required = false) LocalDate rateDate,
      @RequestParam String baseCurrency,
      @RequestParam String quoteCurrency) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "TradingView 日级汇率快照保存成功",
            convertMapper.toResponse(
                exchangeRateService.snapshotTradingView(
                    rateDate, baseCurrency, quoteCurrency))));
  }
}
