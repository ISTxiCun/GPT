package com.istxicun.backend.common.exception;

/** Transport-independent failure classification; adapters choose HTTP/consumer/task handling. */
public enum FailureCategory {
  INVALID_ARGUMENT,
  UNAUTHENTICATED,
  FORBIDDEN,
  NOT_FOUND,
  CONFLICT,
  RATE_LIMITED,
  UNAVAILABLE,
  INTERNAL
}
