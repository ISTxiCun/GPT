package com.istxicun.distributedtest;

import static org.assertj.core.api.Assertions.*;

import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.distributed.messaging.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class MessagingOutboxLeaseIT {
  static class Fixture implements AutoCloseable {
    final javax.sql.DataSource source = MessagingMysqlIT.source();
    final JdbcTemplate jdbc = new JdbcTemplate(source);
    final ApplicationIdentity identity =
        new ApplicationIdentity("lease-it-" + UUID.randomUUID(), "test");
    final String scope = identity.namespace() + ":" + identity.environment();
    final MessagingProperties p;
    final MessageCodec codec;

    Fixture(int batch) {
      Flyway.configure()
          .dataSource(source)
          .locations("classpath:db/messaging")
          .table("flyway_messaging_test")
          .load()
          .migrate();
      p =
          new MessagingProperties(
              true,
              "127.0.0.1",
              5672,
              "/",
              "messaging",
              "messaging-test",
              false,
              100,
              4096,
              1,
              1,
              List.of(),
              false,
              new MessagingProperties.Outbox(true, 100, batch, 2000, 5));
      codec = new MessageCodec(MessagingTestSupport.mapper(), p, identity);
      var writer = new OutboxWriter(source, codec, identity);
      new TransactionTemplate(new DataSourceTransactionManager(source))
          .executeWithoutResult(
              s -> {
                for (int i = 0; i < batch; i++)
                  writer.append(codec.create("lease.event", 1, "data"));
              });
    }

    OutboxDispatcher dispatcher(MessagePublisher publisher) {
      return new OutboxDispatcher(source, identity, p, codec, publisher, new SimpleMeterRegistry());
    }

    int count(String state) {
      return jdbc.queryForObject(
          "SELECT COUNT(*) FROM infra_message_outbox WHERE scope=? AND status=?",
          Integer.class,
          scope,
          state);
    }

    public void close() {
      jdbc.update("DELETE FROM infra_message_outbox WHERE scope=?", scope);
    }
  }

  static void await(CountDownLatch gate) {
    try {
      if (!gate.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }

  @Test
  void slowPublishRenewsCurrentAndQueuedTasksUntilConfirmed() throws Exception {
    try (var f = new Fixture(2)) {
      var entered = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      var calls = new AtomicInteger();
      var pool = Executors.newSingleThreadExecutor();
      var dispatcher =
          f.dispatcher(
              e -> {
                if (calls.incrementAndGet() == 1) {
                  entered.countDown();
                  await(release);
                }
                return new PublishResult(PublishResult.Status.CONFIRMED);
              });
      try {
        var first = pool.submit(dispatcher::dispatchOnce);
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(2700); // Longer than the configured 2000ms lease, not just confirm wait.
        assertThat(
                f.dispatcher(
                        e -> {
                          throw new AssertionError("must not publish");
                        })
                    .claim())
            .isEmpty();
        assertThat(f.count("IN_FLIGHT")).isEqualTo(2);
        release.countDown();
        first.get(10, TimeUnit.SECONDS);
        assertThat(f.count("SENT")).isEqualTo(2);
        assertThat(calls).hasValue(2);
      } finally {
        release.countDown();
        pool.shutdownNow();
      }
    }
  }

  @Test
  void realBlockedAmqpHandshakeDoesNotAllowPrematureReclaim() throws Exception {
    try (var f = new Fixture(1);
        var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      var entered = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      var pool = Executors.newFixedThreadPool(2);
      var peer =
          pool.submit(
              () -> {
                try (var socket = server.accept()) {
                  entered.countDown();
                  await(release);
                } catch (java.io.IOException ex) {
                  throw new RuntimeException(ex);
                }
              });
      var nativeFactory = new com.rabbitmq.client.ConnectionFactory();
      nativeFactory.setHost("127.0.0.1");
      nativeFactory.setPort(server.getLocalPort());
      nativeFactory.setConnectionTimeout(5000);
      nativeFactory.setAutomaticRecoveryEnabled(false);
      var cf = new CachingConnectionFactory(nativeFactory);
      cf.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
      cf.setPublisherReturns(true);
      var template = new RabbitTemplate(cf);
      template.setMandatory(true);
      var publisher =
          new RabbitMessagePublisher(
              template, f.codec, new MessagingNames(f.identity), f.p, new SimpleMeterRegistry());
      try {
        var sending = pool.submit(f.dispatcher(publisher)::dispatchOnce);
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(2700);
        assertThat(sending.isDone()).isFalse();
        assertThat(f.dispatcher(publisher).claim()).isEmpty();
        release.countDown();
        sending.get(10, TimeUnit.SECONDS);
        peer.get(10, TimeUnit.SECONDS);
        assertThat(f.count("PENDING"))
            .isEqualTo(1); // EOF is unknown, not a successful publication.
      } finally {
        release.countDown();
        template.destroy();
        cf.destroy();
        pool.shutdownNow();
      }
    }
  }

  @Test
  void expiredBatchIsNotRevivedAndOldCompletionCannotOverwriteNewOwner() throws Exception {
    try (var f = new Fixture(2)) {
      var entered = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      var calls = new AtomicInteger();
      var pool = Executors.newSingleThreadExecutor();
      var a =
          f.dispatcher(
              e -> {
                calls.incrementAndGet();
                entered.countDown();
                await(release);
                return new PublishResult(PublishResult.Status.CONFIRMED);
              });
      var b =
          f.dispatcher(
              e -> {
                throw new AssertionError("not called");
              });
      try {
        var work = pool.submit(a::dispatchOnce);
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        // Model a missed renewal window. Heartbeats must not resurrect an expired lease.
        f.jdbc.update(
            "UPDATE infra_message_outbox SET"
                + " lease_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP(3)) WHERE scope=?",
            f.scope);
        var recovered = b.claim();
        assertThat(recovered).hasSize(2);
        release.countDown();
        work.get(10, TimeUnit.SECONDS);
        assertThat(calls).hasValue(1); // The old batch must not publish its queued second event.
        assertThat(f.count("IN_FLIGHT")).isEqualTo(2);
        assertThat(f.count("SENT")).isZero();
        for (var row : recovered) b.complete(row, true);
        assertThat(f.count("SENT")).isEqualTo(2);
      } finally {
        release.countDown();
        pool.shutdownNow();
      }
    }
  }

  @Test
  void failedOwnerStopsRenewingAndItsLeaseCanRecover() throws Exception {
    try (var f = new Fixture(1)) {
      var pool = Executors.newSingleThreadExecutor();
      try {
        var work =
            pool.submit(
                f.dispatcher(
                        e -> {
                          throw new AssertionError("simulated worker failure");
                        })
                    ::dispatchOnce);
        assertThatThrownBy(() -> work.get(5, TimeUnit.SECONDS))
            .hasCauseInstanceOf(AssertionError.class);
        assertThat(f.count("IN_FLIGHT")).isEqualTo(1);
        Thread.sleep(2700);
        assertThat(
                f.dispatcher(
                        e -> {
                          throw new AssertionError("not called");
                        })
                    .claim())
            .hasSize(1);
      } finally {
        pool.shutdownNow();
      }
    }
  }
}
