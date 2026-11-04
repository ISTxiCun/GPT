package com.istxicun.distributedtest;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.distributed.messaging.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.*;

class MessagingNamingIT {
  static MessageHandler<String> handler(String name, AtomicInteger calls, CountDownLatch received) {
    return new MessageHandler<>() {
      public String consumerName() {
        return name;
      }

      public String eventType() {
        return "naming.event";
      }

      public int schemaVersion() {
        return 1;
      }

      public TypeReference<String> payloadType() {
        return new TypeReference<>() {};
      }

      public void handle(MessageEnvelope e, String value) {
        calls.incrementAndGet();
        received.countDown();
      }
    };
  }

  @Test
  void ordinaryDottedGroupsConsumeWorkButNeverAnotherGroupsQuarantine() throws Exception {
    var p = MessagingTestSupport.properties(true);
    var cf = new CachingConnectionFactory(p.host(), p.port());
    cf.setUsername(p.username());
    cf.setPassword(p.password());
    cf.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
    cf.setPublisherReturns(true);
    var template = new RabbitTemplate(cf);
    template.setMandatory(true);
    var identity = new ApplicationIdentity("names-it-" + UUID.randomUUID(), "test");
    var names = new MessagingNames(identity);
    var codec = new MessageCodec(MessagingTestSupport.mapper(), p, identity);
    var metrics = new SimpleMeterRegistry();
    var pub = new RabbitMessagePublisher(template, codec, names, p, metrics);
    var calls = new AtomicInteger();
    var received = new CountDownLatch(2);
    var runtime =
        new MessagingRuntime(
            cf,
            pub,
            codec,
            names,
            p,
            metrics,
            List.of(
                handler("orders", calls, received), handler("orders.projection", calls, received)));
    var admin = new RabbitAdmin(cf);
    try {
      runtime.start();
      assertThat(pub.publish(codec.create("naming.event", 1, "work")).confirmed()).isTrue();
      assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(calls).hasValue(2);
      var quarantined = codec.create("naming.event", 1, "failed");
      var props = new MessageProperties();
      props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
      assertThat(
              pub.send(
                      names.failedExchange(),
                      "orders",
                      new Message(codec.encode(quarantined), props))
                  .confirmed())
          .isTrue();
      var retained =
          MessagingRabbitIT.poll(template, names.failedQueue("orders"), Duration.ofSeconds(5));
      assertThat(retained).isNotNull();
      assertThat(codec.decode(retained.getBody()).messageId()).isEqualTo(quarantined.messageId());
      assertThat(calls).hasValue(2);
      assertThat(
              admin
                  .getQueueProperties(names.failedQueue("orders"))
                  .get(RabbitAdmin.QUEUE_CONSUMER_COUNT))
          .isEqualTo(0);
    } finally {
      runtime.stop();
      for (String group : List.of("orders", "orders.projection")) {
        admin.deleteQueue(names.queue(group));
        admin.deleteQueue(names.failedQueue(group));
        for (int i = 0; i < p.retryDelaysMillis().size(); i++)
          admin.deleteQueue(names.retryQueue(group, i));
      }
      for (String exchange :
          List.of(
              names.events(), names.workExchange(), names.retryExchange(), names.failedExchange()))
        admin.deleteExchange(exchange);
      template.destroy();
      cf.destroy();
    }
  }
}
