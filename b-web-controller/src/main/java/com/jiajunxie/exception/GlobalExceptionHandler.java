package com.jiajunxie.exception;

import java.sql.SQLException;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.common.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** 处理主动抛出的业务异常。 */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
    log.warn(
        "Business exception: code={}, message={}", exception.getCode(), exception.getMessage());

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
  }

  /** 处理 @Valid + @RequestBody 校验异常。 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception) {
    String message =
        exception.getBindingResult().getFieldErrors().stream()
            .map(this::formatFieldError)
            .distinct()
            .collect(Collectors.joining("；"));

    if (message.isBlank()) {
      message = CommonErrorCode.VALIDATION_ERROR.getMessage();
    }

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.failure(CommonErrorCode.VALIDATION_ERROR.getCode(), message));
  }

  /** 处理 @RequestParam、@PathVariable 等校验异常。 */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
      ConstraintViolationException exception) {
    String message =
        exception.getConstraintViolations().stream()
            .map(ConstraintViolation::getMessage)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.joining("；"));

    if (message.isBlank()) {
      message = CommonErrorCode.VALIDATION_ERROR.getMessage();
    }

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.failure(CommonErrorCode.VALIDATION_ERROR.getCode(), message));
  }

  /** 处理 JSON 缺失、类型不正确或格式错误。 */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(
      HttpMessageNotReadableException exception) {
    log.warn("Request body is not readable: {}", exception.getMessage());

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            ApiResponse.failure(
                CommonErrorCode.MESSAGE_NOT_READABLE.getCode(),
                CommonErrorCode.MESSAGE_NOT_READABLE.getMessage()));
  }

  /** 处理唯一、非空、外键、CHECK 等数据库完整性异常，避免把所有错误误报成“数据已存在”。 */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
      DataIntegrityViolationException exception) {
    Throwable cause = exception.getMostSpecificCause();
    String databaseMessage = cause == null ? null : cause.getMessage();
    String sqlState = findSqlState(exception);

    log.warn(
        "Database integrity violation: sqlState={}, message={}",
        sqlState,
        databaseMessage);

    if ("23505".equals(sqlState)) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(
              ApiResponse.failure(
                  CommonErrorCode.DUPLICATE_DATA.getCode(),
                  resolveDuplicateMessage(databaseMessage)));
    }

    if ("23503".equals(sqlState)) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(
              ApiResponse.failure(
                  CommonErrorCode.DATA_CONFLICT.getCode(),
                  "关联数据不存在或仍被其他数据引用"));
    }

    if ("23514".equals(sqlState)) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(
              ApiResponse.failure(
                  CommonErrorCode.PARAMETER_ERROR.getCode(),
                  "数据不符合数据库约束"));
    }

    if ("23502".equals(sqlState)) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.failure(
                  CommonErrorCode.SYSTEM_ERROR.getCode(),
                  "系统保存数据失败"));
    }

    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ApiResponse.failure(
                CommonErrorCode.DATA_CONFLICT.getCode(),
                "数据库完整性校验失败"));
  }

  /** 最后的兜底处理，避免把堆栈和数据库细节返回给前端。 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
    log.error("Unhandled system exception", exception);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            ApiResponse.failure(
                CommonErrorCode.SYSTEM_ERROR.getCode(), CommonErrorCode.SYSTEM_ERROR.getMessage()));
  }

  private String formatFieldError(FieldError fieldError) {
    String message = fieldError.getDefaultMessage();

    if (message == null || message.isBlank()) {
      return fieldError.getField() + "参数不正确";
    }

    return message;
  }

  private String findSqlState(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof SQLException sqlException && sqlException.getSQLState() != null) {
        return sqlException.getSQLState();
      }
      current = current.getCause();
    }
    return null;
  }

  private String resolveDuplicateMessage(String databaseMessage) {
    if (databaseMessage == null) {
      return CommonErrorCode.DUPLICATE_DATA.getMessage();
    }

    if (databaseMessage.contains("uk_b_accounts_user_account_code")) {
      return "该用户下的账户编码已存在";
    }

    if (databaseMessage.contains("b_accounts_pkey")) {
      return "相同账户和币种的记录已存在";
    }

    return CommonErrorCode.DUPLICATE_DATA.getMessage();
  }
}
