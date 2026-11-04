package com.istxicun.backend.distributed.messaging;

import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Intended for a restricted operator CLI. Authorization and audit sink are mandatory caller
 * dependencies.
 */
public final class FailedMessageReplayer {
  private final RabbitTemplate template;
  private final RabbitMessagePublisher publisher;
  private final MessagingNames names;
  private long nextAllowed;

  public FailedMessageReplayer(
      RabbitTemplate template, RabbitMessagePublisher publisher, MessagingNames names) {
    this.template = template;
    this.publisher = publisher;
    this.names = names;
  }

  public synchronized boolean replayOne(
      String consumer, String operator, Runnable authorize, BiConsumer<String, String> audit) {
    MessagingNames.component(consumer);
    if (operator == null
        || operator.isBlank()
        || operator.length() > 128
        || authorize == null
        || audit == null)
      throw new IllegalArgumentException("Operator, authorization and audit required");
    authorize.run();
    if (System.nanoTime() < nextAllowed)
      throw new IllegalStateException("Replay rate limited to one per second");
    nextAllowed = System.nanoTime() + 1_000_000_000L;
    String operation = UUID.randomUUID().toString();
    audit.accept(operator, operation + ":START:" + consumer);
    return Boolean.TRUE.equals(
        template.execute(
            channel -> {
              var delivery = channel.basicGet(names.failedQueue(consumer), false);
              if (delivery == null) {
                audit.accept(operator, operation + ":EMPTY");
                return false;
              }
              try {
                if (Boolean.TRUE.equals(
                    delivery.getProps().getHeaders() == null
                        ? null
                        : delivery.getProps().getHeaders().get("x-app-truncated")))
                  throw new IllegalStateException("Truncated quarantine record cannot be replayed");
                var props = new MessageProperties();
                props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                props.setMessageId(delivery.getProps().getMessageId());
                props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                props.setHeader("x-app-attempt", 0);
                props.setHeader("x-app-replay", operation);
                var result =
                    publisher.send(
                        names.workExchange(), consumer, new Message(delivery.getBody(), props));
                if (!result.confirmed())
                  throw new IllegalStateException("Replay not confirmed: result unknown");
                String fingerprint =
                    java.util.HexFormat.of()
                        .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                .digest(delivery.getBody()));
                audit.accept(operator, operation + ":CONFIRMED:" + fingerprint);
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                return true;
              } catch (Exception ex) {
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                throw ex;
              }
            }));
  }
}
