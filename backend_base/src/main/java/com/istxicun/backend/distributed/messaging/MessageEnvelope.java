package com.istxicun.backend.distributed.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MessageEnvelope(
    UUID messageId,
    String eventType,
    int schemaVersion,
    Instant occurredAt,
    String producer,
    JsonNode payload,
    Map<String, String> trace) {
  public MessageEnvelope {
    if (messageId == null
        || eventType == null
        || !eventType.matches("[a-z][a-z0-9.-]{0,63}")
        || schemaVersion < 1
        || schemaVersion > 10000
        || occurredAt == null
        || producer == null
        || !producer.matches("[a-zA-Z0-9_-]{1,64}:[a-zA-Z0-9_-]{1,64}")
        || payload == null) throw new IllegalArgumentException("Invalid message envelope");
    trace = trace == null ? Map.of() : Map.copyOf(trace);
    if (trace.size() > 8
        || trace.entrySet().stream()
            .anyMatch(
                e -> !e.getKey().matches("[a-zA-Z0-9_-]{1,64}") || e.getValue().length() > 512))
      throw new IllegalArgumentException("Invalid trace context");
  }
}
