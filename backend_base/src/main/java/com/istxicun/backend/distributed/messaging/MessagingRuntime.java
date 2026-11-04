package com.istxicun.backend.distributed.messaging;

import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.context.SmartLifecycle;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Quorum retry queues use at-least-once DLX; failed forwards stop consumption for operator
 * recovery.
 */
public final class MessagingRuntime implements SmartLifecycle {
  private static final Logger LOG = LoggerFactory.getLogger(MessagingRuntime.class);
  private final ConnectionFactory connection;
  private final RabbitMessagePublisher publisher;
  private final MessageCodec codec;
  private final MessagingNames names;
  private final MessagingProperties properties;
  private final MeterRegistry metrics;
  private final List<MessageHandler<?>> handlers;
  private final List<SimpleMessageListenerContainer> containers = new ArrayList<>();
  private volatile boolean running;
  private org.springframework.context.ApplicationContext context;

  public void setApplicationContext(org.springframework.context.ApplicationContext context) {
    this.context = context;
  }

  public MessagingRuntime(
      ConnectionFactory connection,
      RabbitMessagePublisher publisher,
      MessageCodec codec,
      MessagingNames names,
      MessagingProperties properties,
      MeterRegistry metrics,
      List<MessageHandler<?>> handlers) {
    this.connection = connection;
    this.publisher = publisher;
    this.codec = codec;
    this.names = names;
    this.properties = properties;
    this.metrics = metrics;
    this.handlers = List.copyOf(handlers);
    Set<String> unique = new HashSet<>();
    for (var handler : handlers) {
      MessagingNames.consumer(handler.consumerName());
      MessagingNames.component(handler.eventType());
      if (!unique.add(handler.consumerName())
          || handler.schemaVersion() < 1
          || handler.schemaVersion() > 10000
          || handler.payloadType() == null)
        throw new IllegalArgumentException("Invalid or duplicate message handler");
    }
  }

  @Override
  public synchronized void start() {
    if (running) return;
    var admin = new RabbitAdmin(connection);
    admin.declareExchange(new DirectExchange(names.events(), true, false));
    admin.declareExchange(new DirectExchange(names.workExchange(), true, false));
    admin.declareExchange(new DirectExchange(names.retryExchange(), true, false));
    admin.declareExchange(new DirectExchange(names.failedExchange(), true, false));
    try {
      for (var handler : handlers) {
        declare(admin, handler);
        if (!properties.consumersEnabled()) continue;
        var container = new SimpleMessageListenerContainer(connection);
        container.setObservationEnabled(true);
        if (context != null) container.setApplicationContext(context);
        container.setShutdownTimeout(30000);
        container.setForceStop(true);
        container.setQueueNames(names.queue(handler.consumerName()));
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        container.setPrefetchCount(properties.prefetch());
        container.setConcurrentConsumers(properties.concurrency());
        container.setDefaultRequeueRejected(false);
        container.setMessageListener(
            (ChannelAwareMessageListener)
                (message, channel) -> consume(handler, message, channel, container));
        containers.add(container);
        container.start();
      }
      running = true;
    } catch (RuntimeException ex) {
      stop();
      throw ex;
    }
  }

  private org.springframework.amqp.core.Queue quorum(String name, Map<String, Object> extra) {
    var args = new HashMap<String, Object>(extra);
    args.put("x-queue-type", "quorum");
    args.put("x-overflow", "reject-publish");
    // No finite broker redelivery limit: business attempts are counted by this adapter.
    args.put("x-delivery-limit", -1);
    return new org.springframework.amqp.core.Queue(name, true, false, false, args);
  }

  private void bind(RabbitAdmin admin, String queue, String exchange, String route) {
    admin.declareBinding(
        new Binding(queue, Binding.DestinationType.QUEUE, exchange, route, Map.of()));
  }

  private void declare(RabbitAdmin admin, MessageHandler<?> handler) {
    String consumer = handler.consumerName();
    admin.declareQueue(quorum(names.queue(consumer), Map.of()));
    admin.declareQueue(quorum(names.failedQueue(consumer), Map.of()));
    bind(admin, names.queue(consumer), names.events(), handler.eventType());
    bind(admin, names.queue(consumer), names.workExchange(), consumer);
    bind(admin, names.failedQueue(consumer), names.failedExchange(), consumer);
    for (int i = 0; i < properties.retryDelaysMillis().size(); i++) {
      admin.declareQueue(
          quorum(
              names.retryQueue(consumer, i),
              Map.of(
                  "x-message-ttl",
                  properties.retryDelaysMillis().get(i),
                  "x-dead-letter-exchange",
                  names.workExchange(),
                  "x-dead-letter-routing-key",
                  consumer,
                  "x-dead-letter-strategy",
                  "at-least-once")));
      bind(admin, names.retryQueue(consumer, i), names.retryExchange(), consumer + "." + i);
    }
  }

  private <T> void consume(
      MessageHandler<T> handler,
      Message original,
      Channel channel,
      SimpleMessageListenerContainer container)
      throws Exception {
    long start = System.nanoTime();
    try {
      consumeObserved(handler, original, channel, container);
    } finally {
      metrics
          .timer("distributed.messaging.consume.duration")
          .record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
    }
  }

  private <T> void consumeObserved(
      MessageHandler<T> handler,
      Message original,
      Channel channel,
      SimpleMessageListenerContainer container)
      throws Exception {
    long tag = original.getMessageProperties().getDeliveryTag();
    int attempt = 0;
    boolean permanent = false;
    try {
      var rawAttempt = original.getMessageProperties().getHeader("x-app-attempt");
      if (rawAttempt != null) {
        if (!(rawAttempt instanceof Integer n)
            || n < 0
            || n > properties.retryDelaysMillis().size())
          throw new NonRetryableMessageException("Invalid retry attempt");
        attempt = (Integer) rawAttempt;
      }
      MessageEnvelope envelope;
      T payload;
      try {
        envelope = codec.decode(original.getBody());
        payload = codec.payload(envelope, handler);
      } catch (IllegalArgumentException ex) {
        throw new NonRetryableMessageException("Invalid message envelope");
      }
      handler.handle(envelope, payload);
      if (TransactionSynchronizationManager.isActualTransactionActive())
        throw new IllegalStateException("Handler returned with an uncommitted transaction");
      channel.basicAck(tag, false);
      metrics.counter("distributed.messaging.consume", "result", "ack").increment();
      return;
    } catch (NonRetryableMessageException ex) {
      permanent = true;
    } catch (Exception ex) {
      LOG.warn("Message handling failed for consumer {}", handler.consumerName(), ex);
    }
    var props = new MessageProperties();
    props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    props.setMessageId(original.getMessageProperties().getMessageId());
    boolean failed = permanent || attempt >= properties.retryDelaysMillis().size();
    props.setHeader("x-app-attempt", failed ? attempt : attempt + 1);
    props.setHeader("x-app-failure", permanent ? "INVALID_MESSAGE" : "HANDLER_FAILED");
    byte[] body = original.getBody();
    if (body.length > properties.maxMessageBytes()) {
      body = Arrays.copyOf(body, properties.maxMessageBytes());
      props.setHeader("x-app-truncated", true);
    }
    var forwarded = new Message(body, props);
    var result =
        publisher.send(
            failed ? names.failedExchange() : names.retryExchange(),
            handler.consumerName() + (failed ? "" : "." + attempt),
            forwarded);
    if (result.confirmed()) {
      channel.basicAck(tag, false);
      metrics
          .counter("distributed.messaging.consume", "result", failed ? "failed" : "retry")
          .increment();
      if (failed) LOG.error("Message quarantined for consumer {}", handler.consumerName());
    } else {
      metrics.counter("distributed.messaging.consume", "result", "forward-unknown").increment();
      LOG.error(
          "Forward not confirmed; pausing consumer {} until operator restart",
          handler.consumerName());
      // Leave the delivery unsettled. Closing the consumer channel returns it to the source queue.
      container.stop(() -> {});
    }
  }

  @Override
  public synchronized void stop() {
    for (var container : containers) container.stop();
    containers.clear();
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    return Integer.MAX_VALUE - 100;
  }
}
