package com.istxicun.distributedtest;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.distributed.messaging.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.*;

class MessagingRabbitIT {
  @Test
  void publishRetryPoisonReplayAndUnavailableDeadLetterTarget() throws Exception {
    var p = MessagingTestSupport.properties(true);
    var cf = new CachingConnectionFactory(p.host(), p.port());
    cf.setUsername(p.username());
    cf.setPassword(p.password());
    cf.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
    cf.setPublisherReturns(true);
    var template = new RabbitTemplate(cf);
    template.setMandatory(true);
    var identity = new ApplicationIdentity("mq-it-" + UUID.randomUUID(), "test");
    var names = new MessagingNames(identity);
    var codec = new MessageCodec(MessagingTestSupport.mapper(), p, identity);
    var metrics = new SimpleMeterRegistry();
    var pub = new RabbitMessagePublisher(template, codec, names, p, metrics);
    AtomicInteger calls = new AtomicInteger();
    AtomicBoolean fail = new AtomicBoolean(true);
    var handled = new CountDownLatch(1);
    MessageHandler<Map<String, Integer>> handler =
        new MessageHandler<>() {
          public String consumerName() {
            return "projection";
          }

          public String eventType() {
            return "stock.changed";
          }

          public int schemaVersion() {
            return 1;
          }

          public TypeReference<Map<String, Integer>> payloadType() {
            return new TypeReference<>() {};
          }

          public void handle(MessageEnvelope e, Map<String, Integer> value) {
            calls.incrementAndGet();
            if (fail.get()) throw new IllegalStateException("retry me");
            handled.countDown();
          }
        };
    var runtime = new MessagingRuntime(cf, pub, codec, names, p, metrics, List.of(handler));
    var admin = new RabbitAdmin(cf);
    try {
      runtime.start();
      assertThat(pub.publish(codec.create("unrouted", 1, Map.of())).status())
          .isEqualTo(PublishResult.Status.RETURNED);
      var envelope = codec.create("stock.changed", 1, Map.of("count", 1));
      assertThat(pub.publish(envelope).confirmed()).isTrue();
      Message failed = poll(template, names.failedQueue("projection"), Duration.ofSeconds(15));
      assertThat(failed).isNotNull();
      assertThat(calls).hasValue(3);
      // Put the failure back, then exercise the audited confirmed-before-ACK replay path.
      assertThat(pub.send(names.failedExchange(), "projection", failed).confirmed()).isTrue();
      fail.set(false);
      var audit = new ArrayList<String>();
      assertThat(
              new FailedMessageReplayer(template, pub, names)
                  .replayOne("projection", "it-operator", () -> {}, (a, b) -> audit.add(b)))
          .isTrue();
      assertThat(handled.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(audit).hasSize(2);
      assertThat(pub.publish(codec.create("stock.changed", 2, Map.of())).confirmed()).isTrue();
      assertThat(poll(template, names.failedQueue("projection"), Duration.ofSeconds(5)))
          .isNotNull();
      assertThat(calls).hasValue(4);
      runtime.stop();
      // Verify TTL's broker-to-broker hop retains a message when its destination binding is absent.
      var binding =
          new Binding(
              names.queue("projection"),
              Binding.DestinationType.QUEUE,
              names.workExchange(),
              "projection",
              Map.of());
      admin.removeBinding(binding);
      var props = new MessageProperties();
      props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
      assertThat(
              pub.send(
                      names.retryExchange(),
                      "projection.0",
                      new Message(codec.encode(envelope), props))
                  .confirmed())
          .isTrue();
      Thread.sleep(1500);
      assertThat(template.receive(names.queue("projection"))).isNull();
      admin.declareBinding(binding);
      assertThat(poll(template, names.queue("projection"), Duration.ofSeconds(20))).isNotNull();
      // If quarantine publication is unroutable, the source delivery must remain recoverable.
      runtime.start();
      admin.removeBinding(
          new Binding(
              names.failedQueue("projection"),
              Binding.DestinationType.QUEUE,
              names.failedExchange(),
              "projection",
              Map.of()));
      var unsupported = codec.create("stock.changed", 99, Map.of());
      assertThat(pub.publish(unsupported).confirmed()).isTrue();
      Message retained = poll(template, names.queue("projection"), Duration.ofSeconds(12));
      assertThat(retained).isNotNull();
      assertThat(codec.decode(retained.getBody()).messageId()).isEqualTo(unsupported.messageId());
    } finally {
      runtime.stop();
      for (int i = 0; i < p.retryDelaysMillis().size(); i++)
        admin.deleteQueue(names.retryQueue("projection", i));
      admin.deleteQueue(names.queue("projection"));
      admin.deleteQueue(names.failedQueue("projection"));
      for (String e :
          List.of(
              names.events(), names.workExchange(), names.retryExchange(), names.failedExchange()))
        admin.deleteExchange(e);
      cf.destroy();
    }
  }

  static Message poll(RabbitTemplate template, String queue, Duration timeout)
      throws InterruptedException {
    long end = System.nanoTime() + timeout.toNanos();
    do {
      Message m = template.receive(queue);
      if (m != null) return m;
      Thread.sleep(50);
    } while (System.nanoTime() < end);
    return null;
  }
}
