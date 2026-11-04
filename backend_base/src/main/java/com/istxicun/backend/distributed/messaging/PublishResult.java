package com.istxicun.backend.distributed.messaging;

/** UNKNOWN means the broker may have accepted the message. Retry with the same messageId. */
public record PublishResult(Status status) {
  public enum Status {
    CONFIRMED,
    RETURNED,
    NACKED,
    UNKNOWN
  }

  public boolean confirmed() {
    return status == Status.CONFIRMED;
  }
}
