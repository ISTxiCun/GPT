package com.istxicun.backend.distributed;

import org.springframework.http.HttpStatus;

public class DistributedException extends RuntimeException {
  private final HttpStatus status;
  private final String code;
  private final Long retryAfter;

  public DistributedException(HttpStatus status, String code, String message) {
    this(status, code, message, null, null);
  }

  public DistributedException(
      HttpStatus status, String code, String message, Long retryAfter, Throwable cause) {
    super(message, cause);
    this.status = status;
    this.code = code;
    this.retryAfter = retryAfter;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  public Long getRetryAfter() {
    return retryAfter;
  }

  public static DistributedException unavailable(Throwable cause) {
    return new DistributedException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "DISTRIBUTED_UNAVAILABLE",
        "基础服务暂时不可用，请确认操作结果后再重试",
        null,
        cause);
  }
}
