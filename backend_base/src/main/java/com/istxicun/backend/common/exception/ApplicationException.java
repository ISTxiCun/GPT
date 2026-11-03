package com.istxicun.backend.common.exception;

import java.util.Objects;

public class ApplicationException extends RuntimeException {
  private final FailureCategory category;
  private final String code;
  private final Long retryAfterSeconds;

  public ApplicationException(
      FailureCategory category,
      String code,
      String message,
      Long retryAfterSeconds,
      Throwable cause) {
    super(message, cause);
    this.category = Objects.requireNonNull(category, "category");
    this.code = Objects.requireNonNull(code, "code");
    if (retryAfterSeconds != null && retryAfterSeconds < 0)
      throw new IllegalArgumentException("Retry delay must be nonnegative");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public FailureCategory getCategory() {
    return category;
  }

  public String getCode() {
    return code;
  }

  public Long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
