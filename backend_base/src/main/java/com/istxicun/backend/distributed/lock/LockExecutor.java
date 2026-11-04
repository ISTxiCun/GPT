package com.istxicun.backend.distributed.lock;

import com.istxicun.backend.common.exception.FailureCategory;
import com.istxicun.backend.distributed.DistributedException;
import com.istxicun.backend.distributed.key.RedisKeyFactory;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Synchronous, same-thread critical section. Start and commit transactions inside the callback. */
public final class LockExecutor {
  private final RedissonClient redisson;
  private final RedisKeyFactory keys;
  private final io.micrometer.core.instrument.MeterRegistry metrics;

  public LockExecutor(RedissonClient redisson, RedisKeyFactory keys) {
    this(redisson, keys, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
  }

  public LockExecutor(
      RedissonClient redisson,
      RedisKeyFactory keys,
      io.micrometer.core.instrument.MeterRegistry metrics) {
    this.redisson = redisson;
    this.keys = keys;
    this.metrics = metrics;
  }

  private void count(String result) {
    metrics.counter("distributed.lock", "result", result).increment();
  }

  public <T> T execute(String logicalKey, Duration waitTime, Supplier<T> action) {
    if (logicalKey == null || logicalKey.isBlank() || logicalKey.length() > 512)
      throw new IllegalArgumentException("Lock key must contain 1..512 characters");
    Objects.requireNonNull(action, "action");
    if (waitTime == null || waitTime.isNegative() || waitTime.compareTo(Duration.ofSeconds(30)) > 0)
      throw new IllegalArgumentException("waitTime must be between 0 and 30 seconds");
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException(
          "Acquire lock outside a transaction; call a separate transactional service inside the"
              + " callback");
    RLock lock = redisson.getLock(keys.key("lock", logicalKey));
    long waitStart = System.nanoTime();
    try {
      if (!lock.tryLock(waitTime.toMillis(), TimeUnit.MILLISECONDS)) {
        count("busy");
        throw new DistributedException(FailureCategory.CONFLICT, "LOCK_BUSY", "操作正在处理中，请稍后重试");
      }
      count("acquired");
    } catch (InterruptedException ex) {
      count("interrupted");
      Thread.currentThread().interrupt();
      throw new DistributedException(
          FailureCategory.UNAVAILABLE, "LOCK_INTERRUPTED", "等待锁被中断", null, ex);
    } catch (RedisException ex) {
      count("backend-failure");
      throw DistributedException.unavailable(ex);
    } finally {
      metrics
          .timer("distributed.lock.wait")
          .record(System.nanoTime() - waitStart, TimeUnit.NANOSECONDS);
    }
    long holdStart = System.nanoTime();
    Throwable primary = null;
    try {
      return action.get();
    } catch (RuntimeException | Error ex) {
      primary = ex;
      throw ex;
    } finally {
      metrics
          .timer("distributed.lock.hold")
          .record(System.nanoTime() - holdStart, TimeUnit.NANOSECONDS);
      try {
        lock.unlock();
      } // Redisson checks thread ownership atomically; never forceUnlock.
      catch (RuntimeException ex) {
        count("release-failure");
        if (primary != null) primary.addSuppressed(ex);
        else
          throw new DistributedException(
              FailureCategory.UNAVAILABLE, "LOCK_RELEASE_FAILED", "锁释放或所有权校验失败，请确认操作结果", null, ex);
      }
    }
  }
}
