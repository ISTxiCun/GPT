package com.istxicun.backend.distributed.messaging;

/** Business implementations must atomically commit their marker and all local business writes. */
public interface ConsumerIdempotency {
  void execute(String consumerName, MessageEnvelope envelope, Runnable businessWork);
}
