package com.jiajunxie.controller;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.common.response.ApiResponse;
import com.jiajunxie.domain.transaction.TransactionLedgerPage;
import com.jiajunxie.domain.transaction.TransactionLedgerQueryCriteria;
import com.jiajunxie.domain.transaction.TransactionLedgerSort;
import com.jiajunxie.domain.transaction.TransactionNature;
import com.jiajunxie.convert.TransactionConvertWebMapper;
import com.jiajunxie.request.TransactionRequest;
import com.jiajunxie.response.TransactionDetailResponse;
import com.jiajunxie.service.TransactionLedgerQueryService;
import com.jiajunxie.service.TransactionService;
import com.jiajunxie.vo.TransactionCreateVo;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.time.ZoneId;
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
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
/**
 * Transaction HTTP adapter.
 *
 * <p>No balance or entry-generation rule may be implemented here. The controller validates the
 * request shape, converts it to the service contract and delegates the atomic accounting operation
 * to {@link TransactionService}.
 */
public class TransactionController {

  private final TransactionService transactionService;
  private final TransactionLedgerQueryService transactionLedgerQueryService;
  private final TransactionConvertWebMapper transactionConvertMapper;

  @GetMapping("/ledger")
  public ResponseEntity<ApiResponse<TransactionLedgerPage>> ledger(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "false") boolean keywordRegex,
      @RequestParam(required = false) String accountId,
      @RequestParam(required = false) String accountTypeCode,
      @RequestParam(required = false) String categoryId,
      @RequestParam(required = false) List<String> personIds,
      @RequestParam(defaultValue = "false") boolean unassignedPerson,
      @RequestParam(required = false) String transactionTypeCode,
      @RequestParam(required = false) String nature,
      @RequestParam(required = false) String currency,
      @RequestParam(required = false) BigDecimal minAmount,
      @RequestParam(required = false) BigDecimal maxAmount,
      @RequestParam(required = false) Boolean readonly,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "50") int pageSize,
      @RequestParam(defaultValue = "TIME_DESC") String sort,
      @RequestParam(defaultValue = "Asia/Tokyo") String timezone) {

    ZoneId zoneId = parseTimezone(timezone);
    LocalDate today = LocalDate.now(zoneId);
    LocalDate effectiveFrom = from;
    LocalDate effectiveTo = to;

    if (effectiveFrom == null && effectiveTo == null) {
      YearMonth month = YearMonth.from(today);
      effectiveFrom = month.atDay(1);
      effectiveTo = month.plusMonths(1).atDay(1);
    } else if (effectiveFrom == null || effectiveTo == null) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR, "from 和 to 必须同时指定；to 为不包含的日期边界");
    }

    if (!effectiveFrom.isBefore(effectiveTo)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "from 必须早于 to");
    }
    if (page < 1) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "page 必须大于等于 1");
    }
    if (pageSize < 1 || pageSize > 200) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "pageSize 必须在 1 到 200 之间");
    }

    TransactionLedgerQueryCriteria criteria =
        new TransactionLedgerQueryCriteria(
            effectiveFrom.atStartOfDay(zoneId).toInstant(),
            effectiveTo.atStartOfDay(zoneId).toInstant(),
            keyword,
            keywordRegex,
            accountId,
            accountTypeCode,
            categoryId,
            personIds,
            unassignedPerson,
            transactionTypeCode,
            parseEnum(nature, TransactionNature.class, "nature"),
            currency,
            minAmount,
            maxAmount,
            readonly,
            page,
            pageSize,
            parseEnum(sort, TransactionLedgerSort.class, "sort"));

    return ResponseEntity.ok(
        ApiResponse.success("全局账单查询成功", transactionLedgerQueryService.query(criteria)));
  }

  private ZoneId parseTimezone(String timezone) {
    try {
      return ZoneId.of(timezone);
    } catch (DateTimeException exception) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "无效的时区：" + timezone);
    }
  }

  private <E extends Enum<E>> E parseEnum(String value, Class<E> type, String parameterName) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Enum.valueOf(type, value.trim().toUpperCase());
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR, parameterName + " 参数值无效：" + value);
    }
  }

  @GetMapping("/{transactionId}")
  public ResponseEntity<ApiResponse<TransactionDetailResponse>> getTransaction(
      @PathVariable String transactionId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "交易详情查询成功",
            transactionConvertMapper.toDetailResponse(
                transactionService.getTransaction(transactionId))));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<String>> createTransaction(
      @Valid @RequestBody TransactionRequest request) {
    TransactionCreateVo transactionVo = transactionConvertMapper.toCreateVo(request);
    String transactionId = transactionService.createTransaction(transactionVo);
    return ResponseEntity.ok(ApiResponse.success("交易创建成功", transactionId));
  }

  @PatchMapping("/{transactionId}")
  public ResponseEntity<ApiResponse<Integer>> updateTransaction(
      @PathVariable String transactionId,
      @Valid @RequestBody TransactionRequest request) {
    TransactionCreateVo transactionVo = transactionConvertMapper.toCreateVo(request);
    return ResponseEntity.ok(
        ApiResponse.success(
            "交易更新成功",
            transactionService.updateTransaction(transactionId, transactionVo)));
  }

  @DeleteMapping("/{transactionId}")
  public ResponseEntity<ApiResponse<Integer>> removeTransaction(
      @PathVariable String transactionId) {
    return ResponseEntity.ok(
        ApiResponse.success("交易删除成功", transactionService.removeTransaction(transactionId)));
  }

  /** 管理/修复接口：依据全部分录重新构建指定账户的余额快照。 */
  @PutMapping("/accounts/{accountId}/recalculate-balance")
  public ResponseEntity<ApiResponse<BigDecimal>> recalculateBalance(
      @PathVariable String accountId) {
    return ResponseEntity.ok(
        ApiResponse.success("账户余额重算成功", transactionService.recalculateBalance(accountId)));
  }
}
