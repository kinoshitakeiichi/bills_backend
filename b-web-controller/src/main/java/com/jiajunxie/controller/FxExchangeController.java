package com.jiajunxie.controller;

import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.request.FxExchangeRequest;
import com.jiajunxie.service.FxExchangeService;
import com.jiajunxie.vo.FxExchangeCreateVo;
import com.jiajunxie.vo.FxExchangeCreatedVo;
import com.jiajunxie.vo.FxPositionVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fx/exchanges")
@RequiredArgsConstructor
public class FxExchangeController {

  private final FxExchangeService service;

  @PostMapping
  public ResponseEntity<ApiResponse<FxExchangeCreatedVo>> create(
      @Valid @RequestBody FxExchangeRequest request) {
    FxExchangeCreateVo command =
        new FxExchangeCreateVo(
            request.getFxDirectionCode(),
            request.getSourceAccountId(),
            request.getTargetAccountId(),
            request.getSourceAmount(),
            request.getSourceCurrency(),
            request.getTargetAmount(),
            request.getTargetCurrency(),
            request.getExecutedRate(),
            request.getFeeAmount(),
            request.getFeeCurrency(),
            request.getRemark(),
            request.getTransactionTime());

    return ResponseEntity.ok(ApiResponse.success("FX 交易创建成功", service.create(command)));
  }

@GetMapping("/positions/{accountId}")
public ResponseEntity<ApiResponse<FxPositionVo>> position(@PathVariable String accountId) {
  return ResponseEntity.ok(
      ApiResponse.success("FX Position 查询成功", service.getPosition(accountId)));
}
}
