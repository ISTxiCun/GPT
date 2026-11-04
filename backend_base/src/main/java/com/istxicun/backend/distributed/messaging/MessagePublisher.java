package com.istxicun.backend.distributed.messaging;

public interface MessagePublisher {
  PublishResult publish(MessageEnvelope envelope);
}
