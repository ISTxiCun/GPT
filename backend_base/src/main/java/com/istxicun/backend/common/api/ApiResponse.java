package com.istxicun.backend.common.api;

import org.slf4j.MDC;

public record ApiResponse<T>(String code, String message, T data, String requestId) {
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>("OK", "success", data, MDC.get("requestId"));
  }

  public static ApiResponse<Void> error(String code, String message) {
    return new ApiResponse<>(code, message, null, MDC.get("requestId"));
  }
}
