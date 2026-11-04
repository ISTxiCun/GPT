package com.istxicun.backend.distributed.messaging;

public class NonRetryableMessageException extends RuntimeException {
  public NonRetryableMessageException(String message) {
    super(message);
  }
}
