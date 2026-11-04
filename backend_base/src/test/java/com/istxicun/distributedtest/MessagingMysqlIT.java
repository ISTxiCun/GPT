package com.istxicun.distributedtest;

import static org.assertj.core.api.Assertions.*;

import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.distributed.messaging.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.*;

class MessagingMysqlIT {
  static DriverManagerDataSource source() {
    return new DriverManagerDataSource(
        System.getenv()
            .getOrDefault(
                "TEST_MYSQL_URL",
                "jdbc:mysql://127.0.0.1:3306/messaging_test?allowPublicKeyRetrieval=true&useSSL=false"),
        System.getenv().getOrDefault("TEST_MYSQL_USER", "root"),
        System.getenv().getOrDefault("TEST_MYSQL_PASSWORD", "messaging-test"));
  }

  @Test
  void concurrentIdempotencyAndOutboxRollbackClaimExpiryFencingAndBudget() throws Exception {
    var source = source();
    var jdbc = new JdbcTemplate(source);
    Flyway.configure()
        .dataSource(source)
        .locations("classpath:db/messaging")
        .table("flyway_messaging_test")
        .load()
        .migrate();
    String scope = "mq-db-" + UUID.randomUUID();
    var identity = new ApplicationIdentity(scope, "test");
    var properties = MessagingTestSupport.properties(false);
    var codec = new MessageCodec(MessagingTestSupport.mapper(), properties, identity);
    var envelope = codec.create("inventory.changed", 1, Map.of("count", 1));
    var idempotency = new JdbcConsumerIdempotency(source, identity);
    var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    String table = "business_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY, count INT NOT NULL)");
    jdbc.update("INSERT INTO " + table + " VALUES (1,0)");
    var pool = Executors.newFixedThreadPool(2);
    try {
      Runnable work = () -> jdbc.update("UPDATE " + table + " SET count=count+1 WHERE id=1");
      assertThatThrownBy(
              () ->
                  idempotency.execute(
                      "projection",
                      envelope,
                      () -> {
                        work.run();
                        throw new IllegalStateException("rollback");
                      }))
          .hasMessage("rollback");
      assertThat(jdbc.queryForObject("SELECT count FROM " + table + " WHERE id=1", Integer.class))
          .isZero();
      var start = new CountDownLatch(1);
      var first =
          pool.submit(
              () -> {
                await(start);
                idempotency.execute("projection", envelope, work);
              });
      var second =
          pool.submit(
              () -> {
                await(start);
                idempotency.execute("projection", envelope, work);
              });
      start.countDown();
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
      assertThat(jdbc.queryForObject("SELECT count FROM " + table + " WHERE id=1", Integer.class))
          .isEqualTo(1);
      var writer = new OutboxWriter(source, codec, identity);
      assertThatThrownBy(() -> writer.append(envelope)).isInstanceOf(IllegalStateException.class);
      tx.executeWithoutResult(
          s -> {
            writer.append(envelope);
            s.setRollbackOnly();
          });
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM infra_message_outbox WHERE scope=?",
                  Integer.class,
                  scope + ":test"))
          .isZero();
      tx.executeWithoutResult(s -> writer.append(envelope));
      MessagePublisher publisher =
          e -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new PublishResult(PublishResult.Status.CONFIRMED);
          };
      var a =
          new OutboxDispatcher(
              source, identity, properties, codec, publisher, new SimpleMeterRegistry());
      var b =
          new OutboxDispatcher(
              source, identity, properties, codec, publisher, new SimpleMeterRegistry());
      var c = a.claim();
      assertThat(c).hasSize(1);
      assertThat(b.claim()).isEmpty();
      // Simulate a crashed claimant whose lease has expired, then verify the old token cannot
      // complete it.
      jdbc.update(
          "UPDATE infra_message_outbox SET lease_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP(3))"
              + " WHERE scope=?",
          scope + ":test");
      var recovered = b.claim();
      assertThat(recovered).hasSize(1);
      a.complete(c.get(0), true);
      assertThat(
              jdbc.queryForObject(
                  "SELECT status FROM infra_message_outbox WHERE scope=?",
                  String.class,
                  scope + ":test"))
          .isEqualTo("IN_FLIGHT");
      b.complete(recovered.get(0), true);
      assertThat(
              jdbc.queryForObject(
                  "SELECT status FROM infra_message_outbox WHERE scope=?",
                  String.class,
                  scope + ":test"))
          .isEqualTo("SENT");
      var poison = codec.create("inventory.changed", 1, Map.of());
      tx.executeWithoutResult(s -> writer.append(poison));
      for (int i = 0; i < 3; i++) {
        var claim = a.claim();
        assertThat(claim).hasSize(1);
        a.complete(claim.get(0), false);
        jdbc.update(
            "UPDATE infra_message_outbox SET"
                + " next_attempt=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP(3)) WHERE scope=? AND"
                + " status='PENDING'",
            scope + ":test");
      }
      assertThat(a.claim()).isEmpty();
      assertThat(
              jdbc.queryForObject(
                  "SELECT status FROM infra_message_outbox WHERE scope=? AND message_id=?",
                  String.class,
                  scope + ":test",
                  poison.messageId().toString()))
          .isEqualTo("FAILED");
      var one = codec.create("inventory.changed", 1, Map.of());
      var two = codec.create("inventory.changed", 1, Map.of());
      tx.executeWithoutResult(
          s -> {
            writer.append(one);
            writer.append(two);
          });
      var together = new CountDownLatch(1);
      var claimedA =
          pool.submit(
              () -> {
                await(together);
                return a.claim();
              });
      var claimedB =
          pool.submit(
              () -> {
                await(together);
                return b.claim();
              });
      together.countDown();
      var rowsA = claimedA.get(10, TimeUnit.SECONDS);
      var rowsB = claimedB.get(10, TimeUnit.SECONDS);
      // SKIP LOCKED may return an empty batch while another scan holds eligible rows.
      // It guarantees exclusion, not fairness or a full batch on every concurrent poll.
      var concurrent = new java.util.ArrayList<OutboxDispatcher.Claim>();
      concurrent.addAll(rowsA);
      concurrent.addAll(rowsB);
      assertThat(concurrent).isNotEmpty();
      assertThat(concurrent.stream().map(OutboxDispatcher.Claim::id).toList())
          .doesNotHaveDuplicates();
      for (var row : concurrent) a.complete(row, true);
      var later = b.claim();
      var allIds = new java.util.ArrayList<String>();
      concurrent.forEach(row -> allIds.add(row.id()));
      later.forEach(
          row -> {
            allIds.add(row.id());
            b.complete(row, true);
          });
      assertThat(allIds)
          .containsExactlyInAnyOrder(one.messageId().toString(), two.messageId().toString());
    } finally {
      pool.shutdownNow();
      jdbc.execute("DROP TABLE " + table);
      jdbc.update("DELETE FROM infra_message_consumed WHERE scope=?", scope + ":test");
      jdbc.update("DELETE FROM infra_message_outbox WHERE scope=?", scope + ":test");
    }
  }

  static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
