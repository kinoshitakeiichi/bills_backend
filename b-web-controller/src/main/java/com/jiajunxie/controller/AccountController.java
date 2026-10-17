package com.jiajunxie.controller;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
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
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.convert.AccountConvertWebMapper;
import com.jiajunxie.convert.TransactionConvertWebMapper;
import com.jiajunxie.request.AccountRequest;
import com.jiajunxie.response.AccountOptionResponse;
import com.jiajunxie.response.AccountResponse;
import com.jiajunxie.response.AccountTransactionMonthResponse;
import com.jiajunxie.service.AccountService;
import com.jiajunxie.service.TransactionService;
import com.jiajunxie.vo.AccountVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
/**
 * Account HTTP adapter.
 *
 * <p>The controller is intentionally limited to request validation, Web/VO conversion and service
 * invocation. Account rules such as owner validation, parent-account validation and account-class
 * derivation belong to the service layer.
 */
public class AccountController {

  private final AccountService accountService;
  private final AccountConvertWebMapper accountConvertMapper;
  private final TransactionService transactionService;
  private final TransactionConvertWebMapper transactionConvertMapper;

  @PostMapping
  public ResponseEntity<ApiResponse<Integer>> addAccount(
      @Valid @RequestBody AccountRequest accountRequest) {

    AccountVo accountVo = accountConvertMapper.toVo(accountRequest);
    return ResponseEntity.ok(ApiResponse.success("账户创建成功", accountService.addAccount(accountVo)));
  }

  @GetMapping()
  public ResponseEntity<ApiResponse<List<AccountResponse>>> allAccounts() {
    List<AccountResponse> accountResponse =
        accountConvertMapper.toResponses(accountService.cardList());
    return ResponseEntity.ok(ApiResponse.success("获取全部账户成功", accountResponse));
  }

  @GetMapping("/options")
  public ResponseEntity<ApiResponse<List<AccountOptionResponse>>> options(
      @RequestParam(required = false) String accountClassCode,
      @RequestParam(required = false) String accountTypeCode,
      @RequestParam(required = false) Boolean baseOnly) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "获取账户选项成功",
            accountConvertMapper.toOptionResponses(
                accountService.optionList(accountClassCode, accountTypeCode, baseOnly))));
  }

  @PatchMapping()
  public ResponseEntity<ApiResponse<Integer>> updateMaster(
      @Valid @RequestBody AccountRequest request) {

    AccountVo masterData = accountConvertMapper.toVo(request);

    return ResponseEntity.ok(
        ApiResponse.success("主数据更新成功", accountService.updateMasterData(masterData)));
  }

  @GetMapping("/{accountId}/transactions")
  public ResponseEntity<ApiResponse<AccountTransactionMonthResponse>> accountTransactions(
      @PathVariable String accountId,
      @RequestParam(required = false) String month,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "Asia/Tokyo") String timezone) {

    YearMonth queryMonth = parseMonth(month);
    ZoneId queryTimezone = parseTimezone(timezone);
    AccountTransactionMonthResponse response =
        transactionConvertMapper.toMonthResponse(
            transactionService.listAccountTransactions(
                accountId, queryMonth, keyword, queryTimezone));

    return ResponseEntity.ok(ApiResponse.success("获取账户交易成功", response));
  }

  /** Parses the optional yyyy-MM query parameter without leaking date parsing into the service. */
  private YearMonth parseMonth(String month) {
    if (month == null || month.isBlank()) {
      return null;
    }
    try {
      return YearMonth.parse(month.trim());
    } catch (DateTimeException exception) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "month 格式必须为 yyyy-MM");
    }
  }

  /** Converts the client timezone into a ZoneId used to calculate month boundaries correctly. */
  private ZoneId parseTimezone(String timezone) {
    try {
      return ZoneId.of(timezone);
    } catch (DateTimeException exception) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "无效的时区：" + timezone);
    }
  }

  @PatchMapping("/{accountId}/enabled")
  public ResponseEntity<ApiResponse<Integer>> setEnabled(
      @PathVariable String accountId, @RequestParam boolean enabled) {
    return ResponseEntity.ok(
        ApiResponse.success(
            enabled ? "账户启用成功" : "账户停用成功",
            accountService.setEnabled(accountId, enabled)));
  }

  @DeleteMapping("/{accountId}")
  public ResponseEntity<ApiResponse<Integer>> removeAccount(
      @PathVariable("accountId") String accountId) {
    return ResponseEntity.ok(
        ApiResponse.success("账户删除成功", accountService.removeAccount(accountId)));
  }
}
