package com.istxicun.backend.distributed.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class RabbitMessagePublisher implements MessagePublisher {
  private final RabbitTemplate template;
  private final MessageCodec codec;
  private final MessagingNames names;
  private final MessagingProperties properties;
  private final MeterRegistry metrics;

  public RabbitMessagePublisher(
      RabbitTemplate template,
      MessageCodec codec,
      MessagingNames names,
      MessagingProperties properties,
      MeterRegistry metrics) {
    this.template = template;
    this.codec = codec;
    this.names = names;
    this.properties = properties;
    this.metrics = metrics;
  }

  @Override
  public PublishResult publish(MessageEnvelope envelope) {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException(
          "Publish outside database transactions; use OutboxWriter inside a transaction");
    var props = new MessageProperties();
    props.setMessageId(envelope.messageId().toString());
    props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    return send(names.events(), envelope.eventType(), new Message(codec.encode(envelope), props));
  }

  public PublishResult send(String exchange, String route, Message message) {
    if (!java.util.Set.of(
            names.events(), names.workExchange(), names.retryExchange(), names.failedExchange())
        .contains(exchange))
      throw new IllegalArgumentException("Exchange outside application/environment scope");
    if (route == null || !route.matches("[a-z][a-z0-9.-]{0,65}"))
      throw new IllegalArgumentException("Invalid route");
    if (message.getBody().length > properties.maxMessageBytes())
      throw new IllegalArgumentException("Message exceeds configured size");
    long started = System.nanoTime();
    var correlation = new CorrelationData(UUID.randomUUID().toString());
    PublishResult.Status status;
    try {
      template.send(exchange, route, message, correlation);
      var confirm =
          correlation.getFuture().get(properties.confirmTimeoutMillis(), TimeUnit.MILLISECONDS);
      status =
          correlation.getReturned() != null
              ? PublishResult.Status.RETURNED
              : confirm.isAck() ? PublishResult.Status.CONFIRMED : PublishResult.Status.NACKED;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      status = PublishResult.Status.UNKNOWN;
    } catch (Exception ex) {
      status = PublishResult.Status.UNKNOWN;
    }
    metrics
        .timer("distributed.messaging.publish.confirm")
        .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
    metrics.counter("distributed.messaging.publish", "result", status.name()).increment();
    return new PublishResult(status);
  }
}
