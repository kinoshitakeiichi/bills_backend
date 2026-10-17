package com.jiajunxie.common.exception;

public enum CommonErrorCode implements ErrorCode {
  PARAMETER_ERROR(40000, "请求参数不正确"),
  VALIDATION_ERROR(40001, "请求参数校验失败"),
  MESSAGE_NOT_READABLE(40002, "请求体格式不正确"),

  DATA_NOT_FOUND(40400, "请求的数据不存在"),

  DATA_CONFLICT(40900, "数据状态冲突"),
  DUPLICATE_DATA(40901, "数据已存在"),

  SYSTEM_ERROR(50000, "系统内部错误");

  private final Integer code;
  private final String message;

  CommonErrorCode(Integer code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public Integer getCode() {
    return code;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
