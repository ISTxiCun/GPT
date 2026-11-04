package com.istxicun.backend.distributed.messaging;

import com.istxicun.backend.common.identity.ApplicationIdentity;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Claim transactions end before broker I/O. A fencing token guards every completion update. */
public final class OutboxDispatcher implements SmartLifecycle {
  public record Claim(String id, String body, String token, int attempts) {}

  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final String scope;
  private final MessagingProperties.Outbox config;
  private final MessageCodec codec;
  private final MessagePublisher publisher;
  private final MeterRegistry metrics;
  private ScheduledExecutorService worker;
  private volatile boolean stopping;
  private final JdbcTemplate leaseJdbc;

  public OutboxDispatcher(
      DataSource source,
      ApplicationIdentity identity,
      MessagingProperties p,
      MessageCodec codec,
      MessagePublisher publisher,
      MeterRegistry metrics) {
    jdbc = new JdbcTemplate(source);
    tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    tx.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    scope = identity.namespace() + ":" + identity.environment();
    config = p.outbox();
    leaseJdbc = new JdbcTemplate(source);
    leaseJdbc.setQueryTimeout(Math.max(1, config.leaseMillis() / 3000));
    this.codec = codec;
    this.publisher = publisher;
    this.metrics = metrics;
    if (config.leaseMillis() <= (long) p.confirmTimeoutMillis() * config.batchSize() + 1000)
      throw new IllegalArgumentException(
          "Outbox lease must exceed batch confirm budget plus 1000ms");
  }

  public List<Claim> claim() {
    return tx.execute(
        status -> {
          var rows =
              jdbc.queryForList(
                  "SELECT message_id,body,attempts FROM infra_message_outbox WHERE scope=? AND"
                      + " ((status='PENDING' AND next_attempt<=CURRENT_TIMESTAMP(3)) OR"
                      + " (status='IN_FLIGHT' AND lease_until<=CURRENT_TIMESTAMP(3))) ORDER BY"
                      + " created_at LIMIT ? FOR UPDATE SKIP LOCKED",
                  scope,
                  config.batchSize());
          List<Claim> claims = new ArrayList<>();
          for (var row : rows) {
            String id = (String) row.get("message_id");
            int attempts = ((Number) row.get("attempts")).intValue();
            if (attempts >= config.maxAttempts()) {
              jdbc.update(
                  "UPDATE infra_message_outbox SET"
                      + " status='FAILED',claim_token=NULL,lease_until=NULL WHERE scope=? AND"
                      + " message_id=?",
                  scope,
                  id);
              metrics.counter("distributed.messaging.outbox", "result", "exhausted").increment();
              LoggerFactory.getLogger(getClass()).error("Outbox retry budget exhausted");
              continue;
            }
            String token = UUID.randomUUID().toString();
            jdbc.update(
                "UPDATE infra_message_outbox SET"
                    + " status='IN_FLIGHT',claim_token=?,lease_until=TIMESTAMPADD(MICROSECOND,?,CURRENT_TIMESTAMP(3)),attempts=attempts+1"
                    + " WHERE scope=? AND message_id=?",
                token,
                (long) config.leaseMillis() * 1000,
                scope,
                id);
            claims.add(new Claim(id, (String) row.get("body"), token, attempts + 1));
          }
          return claims;
        });
  }

  public void complete(Claim claim, boolean confirmed) {
    int updated;
    if (confirmed)
      updated =
          jdbc.update(
              "UPDATE infra_message_outbox SET"
                  + " status='SENT',sent_at=CURRENT_TIMESTAMP(3),claim_token=NULL,lease_until=NULL"
                  + " WHERE scope=? AND message_id=? AND status='IN_FLIGHT' AND claim_token=?",
              scope,
              claim.id(),
              claim.token());
    else
      updated =
          jdbc.update(
              "UPDATE infra_message_outbox SET"
                  + " status='PENDING',next_attempt=TIMESTAMPADD(SECOND,?,CURRENT_TIMESTAMP(3)),claim_token=NULL,lease_until=NULL"
                  + " WHERE scope=? AND message_id=? AND status='IN_FLIGHT' AND claim_token=?",
              Math.min(3600, 1L << Math.min(12, claim.attempts())),
              scope,
              claim.id(),
              claim.token());
    metrics
        .counter(
            "distributed.messaging.outbox",
            "result",
            updated == 0 ? "stale-claim" : confirmed ? "sent" : "retry")
        .increment();
  }

  public void dispatchOnce() {
    if (org.springframework.transaction.support.TransactionSynchronizationManager
        .isActualTransactionActive())
      throw new IllegalStateException("Dispatch outside database transactions");
    List<Claim> batch = claim();
    if (batch.isEmpty()) return;
    try (var leases = new BatchLeases(batch)) {
      for (var claim : batch) {
        if (Thread.currentThread().isInterrupted() || stopping) return;
        // Verify ownership immediately before publishing. Never resurrect an already expired lease.
        if (!leases.owned(claim)) continue;
        boolean confirmed = false;
        try {
          confirmed =
              publisher
                  .publish(codec.decode(claim.body().getBytes(StandardCharsets.UTF_8)))
                  .confirmed();
        } catch (RuntimeException ex) {
          LoggerFactory.getLogger(getClass()).warn("Outbox publication failed", ex);
        }
        leases.finished(claim);
        complete(claim, confirmed);
      }
    }
  }

  /** Renews both the current publication and tasks waiting behind it in the claimed batch. */
  private final class BatchLeases implements AutoCloseable {
    private final ConcurrentMap<String, Claim> active = new ConcurrentHashMap<>();
    private final Thread owner = Thread.currentThread();
    private final java.util.concurrent.atomic.AtomicBoolean healthy =
        new java.util.concurrent.atomic.AtomicBoolean(true);
    private final ScheduledExecutorService timer =
        Executors.newSingleThreadScheduledExecutor(
            task -> {
              var thread = new Thread(task, "outbox-lease-renewal");
              thread.setDaemon(true);
              return thread;
            });

    BatchLeases(List<Claim> claims) {
      claims.forEach(claim -> active.put(claim.id(), claim));
      long interval = Math.max(100, config.leaseMillis() / 3);
      timer.scheduleWithFixedDelay(this::renew, interval, interval, TimeUnit.MILLISECONDS);
    }

    boolean owned(Claim claim) {
      if (!healthy.get() || !active.containsKey(claim.id())) return false;
      try {
        int updated =
            leaseJdbc.update(
                "UPDATE infra_message_outbox SET"
                    + " lease_until=TIMESTAMPADD(MICROSECOND,?,CURRENT_TIMESTAMP(3)) WHERE scope=?"
                    + " AND message_id=? AND status='IN_FLIGHT' AND claim_token=? AND"
                    + " lease_until>CURRENT_TIMESTAMP(3)",
                (long) config.leaseMillis() * 1000,
                scope,
                claim.id(),
                claim.token());
        if (updated == 1) return true;
        if (active.remove(claim.id(), claim))
          metrics.counter("distributed.messaging.outbox", "result", "lease-lost").increment();
      } catch (RuntimeException ex) {
        healthy.set(false);
        active.clear();
        timer.shutdown();
        LoggerFactory.getLogger(OutboxDispatcher.class)
            .error(
                "Outbox lease renewal failed; no further tasks in this batch will be published",
                ex);
      }
      return false;
    }

    private void renew() {
      if (!owner.isAlive() || owner.isInterrupted() || stopping) {
        healthy.set(false);
        active.clear();
        timer.shutdown();
        return;
      }
      for (var claim : active.values()) {
        if (!healthy.get()) return;
        owned(claim);
      }
    }

    void finished(Claim claim) {
      active.remove(claim.id(), claim);
    }

    @Override
    public void close() {
      healthy.set(false);
      active.clear();
      timer.shutdownNow();
    }
  }

  /** Explicit bounded retention operation. Never removes pending, failed or in-flight intents. */
  public int purgeSentBefore(Instant before, int limit) {
    if (limit < 1 || limit > 1000 || before.isAfter(Instant.now().minus(Duration.ofDays(1))))
      throw new IllegalArgumentException("Keep at least one day; purge limit 1..1000");
    return jdbc.update(
        "DELETE FROM infra_message_outbox WHERE scope=? AND status='SENT' AND sent_at<? LIMIT ?",
        scope,
        Timestamp.from(before),
        limit);
  }

  @Override
  public synchronized void start() {
    if (worker != null) return;
    stopping = false;
    jdbc.queryForObject(
        "SELECT COUNT(*) FROM infra_message_outbox WHERE scope=?", Long.class, scope);
    worker = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "message-outbox"));
    worker.scheduleWithFixedDelay(
        () -> {
          try {
            dispatchOnce();
          } catch (RuntimeException ex) {
            LoggerFactory.getLogger(getClass()).error("Outbox polling failed", ex);
          }
        },
        config.pollMillis(),
        config.pollMillis(),
        TimeUnit.MILLISECONDS);
  }

  @Override
  public synchronized void stop() {
    stopping = true;
    if (worker == null) return;
    worker.shutdown();
    try {
      if (!worker.awaitTermination(30, TimeUnit.SECONDS)) worker.shutdownNow();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      worker.shutdownNow();
    }
    worker = null;
  }

  @Override
  public synchronized boolean isRunning() {
    return worker != null;
  }

  @Override
  public int getPhase() {
    return Integer.MAX_VALUE - 50;
  }
}
