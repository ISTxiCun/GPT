package com.istxicun.distributedtest;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.distributed.messaging.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** The child commits its business work and dies before returning to the adapter's basicAck. */
class MessagingCrashIT {
  static class Node implements AutoCloseable {
    final CachingConnectionFactory cf;
    final RabbitMessagePublisher publisher;
    final MessageCodec codec;
    final MessagingNames names;
    final MessagingRuntime runtime;

    Node(String namespace, boolean consumer, MessageHandler<Map<String, Integer>> handler) {
      var p = MessagingTestSupport.properties(consumer);
      var identity = new ApplicationIdentity(namespace, "test");
      cf = new CachingConnectionFactory(p.host(), p.port());
      cf.setUsername(p.username());
      cf.setPassword(p.password());
      cf.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
      cf.setPublisherReturns(true);
      var template = new RabbitTemplate(cf);
      template.setMandatory(true);
      var metrics = new SimpleMeterRegistry();
      codec = new MessageCodec(MessagingTestSupport.mapper(), p, identity);
      names = new MessagingNames(identity);
      publisher = new RabbitMessagePublisher(template, codec, names, p, metrics);
      runtime = new MessagingRuntime(cf, publisher, codec, names, p, metrics, List.of(handler));
      runtime.start();
    }

    public void close() {
      runtime.stop();
      cf.destroy();
    }
  }

  static MessageHandler<Map<String, Integer>> handler(
      String namespace, String table, boolean crash, CountDownLatch done) {
    var source = MessagingMysqlIT.source();
    var jdbc = new JdbcTemplate(source);
    var idem = new JdbcConsumerIdempotency(source, new ApplicationIdentity(namespace, "test"));
    return new MessageHandler<>() {
      public String consumerName() {
        return "crash-projection";
      }

      public String eventType() {
        return "inventory.changed";
      }

      public int schemaVersion() {
        return 1;
      }

      public TypeReference<Map<String, Integer>> payloadType() {
        return new TypeReference<>() {};
      }

      public void handle(MessageEnvelope envelope, Map<String, Integer> payload) {
        idem.execute(
            consumerName(),
            envelope,
            () -> jdbc.update("UPDATE " + table + " SET count=count+1 WHERE id=1"));
        if (crash) Runtime.getRuntime().halt(17);
        done.countDown();
      }
    };
  }

  @Test
  void databaseCommitSurvivesCrashBeforeAckAndRedeliveryDoesNotRepeatBusinessWrite()
      throws Exception {
    var source = MessagingMysqlIT.source();
    var jdbc = new JdbcTemplate(source);
    Flyway.configure()
        .dataSource(source)
        .locations("classpath:db/messaging")
        .table("flyway_messaging_test")
        .load()
        .migrate();
    String namespace = "mq-crash-" + UUID.randomUUID(),
        table = "crash_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY,count INT NOT NULL)");
    jdbc.update("INSERT INTO " + table + " VALUES(1,0)");
    var done = new CountDownLatch(1);
    try (var producer = new Node(namespace, false, handler(namespace, table, false, done))) {
      assertThat(
              producer
                  .publisher
                  .publish(producer.codec.create("inventory.changed", 1, Map.of("count", 1)))
                  .confirmed())
          .isTrue();
      var child =
          new ProcessBuilder(
                  System.getProperty("java.home") + "/bin/java",
                  "-cp",
                  System.getProperty(
                      "surefire.test.class.path", System.getProperty("java.class.path")),
                  MessagingCrashIT.class.getName(),
                  namespace,
                  table)
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .start();
      try {
        assertThat(child.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(child.exitValue()).isEqualTo(17);
        assertThat(jdbc.queryForObject("SELECT count FROM " + table + " WHERE id=1", Integer.class))
            .isEqualTo(1);
        try (var recovered = new Node(namespace, true, handler(namespace, table, false, done))) {
          assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
          assertThat(
                  jdbc.queryForObject("SELECT count FROM " + table + " WHERE id=1", Integer.class))
              .isEqualTo(1);
        }
      } finally {
        child.destroyForcibly();
      }
      var admin = new RabbitAdmin(producer.cf);
      admin.deleteQueue(producer.names.queue("crash-projection"));
      admin.deleteQueue(producer.names.failedQueue("crash-projection"));
      for (int i = 0; i < 2; i++)
        admin.deleteQueue(producer.names.retryQueue("crash-projection", i));
      for (String exchange :
          List.of(
              producer.names.events(),
              producer.names.workExchange(),
              producer.names.retryExchange(),
              producer.names.failedExchange())) admin.deleteExchange(exchange);
    } finally {
      jdbc.execute("DROP TABLE " + table);
      jdbc.update("DELETE FROM infra_message_consumed WHERE scope=?", namespace + ":test");
    }
  }

  public static void main(String[] args) throws Exception {
    try (var node =
        new Node(args[0], true, handler(args[0], args[1], true, new CountDownLatch(1)))) {
      Thread.sleep(60000);
    }
  }
}
