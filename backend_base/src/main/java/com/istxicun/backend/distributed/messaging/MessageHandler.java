package com.istxicun.backend.distributed.messaging;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * handle() must finish its local transaction before returning. External effects need business
 * idempotency.
 */
public interface MessageHandler<T> {
  String consumerName();

  String eventType();

  int schemaVersion();

  TypeReference<T> payloadType();

  void handle(MessageEnvelope envelope, T payload);
}
