package com.istxicun.backend.distributed.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class MessageCodec {
  private final ObjectMapper mapper;
  private final int maxBytes;
  private final String producer;

  public MessageCodec(
      ObjectMapper mapper, MessagingProperties properties, ApplicationIdentity identity) {
    this.mapper =
        mapper
            .copy()
            .deactivateDefaultTyping()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    this.maxBytes = properties.maxMessageBytes();
    producer = identity.namespace() + ":" + identity.environment();
  }

  public MessageEnvelope create(String eventType, int version, Object payload) {
    return new MessageEnvelope(
        UUID.randomUUID(),
        eventType,
        version,
        Instant.now(),
        producer,
        mapper.valueToTree(payload),
        Map.of());
  }

  public byte[] encode(MessageEnvelope envelope) {
    try {
      byte[] bytes = mapper.writeValueAsBytes(envelope);
      if (bytes.length > maxBytes)
        throw new IllegalArgumentException("Message exceeds configured size");
      return bytes;
    } catch (java.io.IOException ex) {
      throw new IllegalArgumentException("Cannot encode message", ex);
    }
  }

  public MessageEnvelope decode(byte[] bytes) {
    if (bytes.length > maxBytes)
      throw new IllegalArgumentException("Message exceeds configured size");
    try {
      var envelope = mapper.readValue(bytes, MessageEnvelope.class);
      if (envelope == null) throw new IllegalArgumentException("Null envelope");
      return envelope;
    } catch (java.io.IOException ex) {
      throw new IllegalArgumentException("Invalid envelope", ex);
    }
  }

  public <T> T payload(MessageEnvelope envelope, MessageHandler<T> handler) {
    if (!envelope.eventType().equals(handler.eventType())
        || envelope.schemaVersion() != handler.schemaVersion())
      throw new NonRetryableMessageException("Unsupported event/schema version");
    try {
      return mapper.readerFor(handler.payloadType()).readValue(envelope.payload());
    } catch (java.io.IOException ex) {
      throw new NonRetryableMessageException("Invalid event payload");
    }
  }
}
