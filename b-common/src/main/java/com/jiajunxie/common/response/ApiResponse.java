package com.jiajunxie.common.response;

public record ApiResponse<T>(Integer code, String message, T data) {

  private static final int SUCCESS_CODE = 0;

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(SUCCESS_CODE, "操作成功", data);
  }

  public static <T> ApiResponse<T> success(String message, T data) {
    return new ApiResponse<>(SUCCESS_CODE, message, data);
  }

  public static ApiResponse<Void> failure(Integer code, String message) {
    return new ApiResponse<>(code, message, null);
  }

  public static <T> ApiResponse<T> failure(Integer code, String message, T data) {
    return new ApiResponse<>(code, message, data);
  }
}
