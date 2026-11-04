package com.istxicun.backend.distributed.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.istxicun.backend.common.exception.FailureCategory;
import com.istxicun.backend.distributed.DistributedException;
import com.istxicun.backend.distributed.key.RedisKeyFactory;
import com.istxicun.backend.distributed.lock.LockExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Synchronous read-through cache. Loaders must be side-effect-free reads. */
public final class CacheClient {
  private static final Logger LOG = LoggerFactory.getLogger(CacheClient.class);
  private final StringRedisTemplate redis;
  private final RedisKeyFactory keys;
  private final ObjectMapper mapper;
  private final CacheProperties config;
  private final LockExecutor locks;
  private final MeterRegistry metrics;
  private final Semaphore fallback;

  public CacheClient(
      StringRedisTemplate redis,
      RedisKeyFactory keys,
      ObjectMapper mapper,
      CacheProperties config,
      LockExecutor locks,
      MeterRegistry metrics) {
    this.redis = redis;
    this.keys = keys;
    this.mapper = mapper.copy();
    this.mapper.deactivateDefaultTyping();
    this.config = config;
    this.locks = locks;
    this.metrics = metrics;
    fallback = new Semaphore(config.fallbackConcurrency());
    requireLock(config.policy());
  }

  private void requireLock(CachePolicy policy) {
    if (policy.protectLoads() && locks == null)
      throw new IllegalArgumentException(
          "cache protect-loads requires app.distributed.lock.enabled=true");
  }

  public String physicalKey(CacheKey key) {
    return keys.key("cache", key.region(), key.version(), key.businessId());
  }

  private void count(String event) {
    metrics.counter("distributed.cache", "event", event).increment();
  }

  private DistributedException failure(String code, Throwable cause) {
    return new DistributedException(
        FailureCategory.UNAVAILABLE, code, "Cache operation failed", null, cause);
  }

  public <T> CacheResult<T> get(CacheKey key, CacheType<T> type) {
    Objects.requireNonNull(type);
    String raw;
    try {
      raw = redis.opsForValue().get(physicalKey(key));
    } catch (DataAccessException ex) {
      count("read-failure");
      throw failure("CACHE_READ_FAILED", ex);
    }
    if (raw == null) {
      count("miss");
      return CacheResult.miss();
    }
    try {
      if (raw.getBytes(StandardCharsets.UTF_8).length > config.maxPayloadBytes())
        throw new IllegalArgumentException("Oversized cache entry");
      var node = mapper.readTree(raw);
      if (!node.isObject()
          || !node.path("format").isIntegralNumber()
          || !node.path("format").canConvertToInt()
          || node.path("format").intValue() != 1
          || !node.path("type").asText().equals(type.javaType().toCanonical())
          || !node.path("nil").isBoolean()
          || !node.has("payload")
          || node.path("nil").booleanValue() != node.get("payload").isNull())
        throw new IllegalArgumentException("Invalid cache envelope");
      T value =
          node.path("nil").booleanValue()
              ? null
              : mapper.readerFor(type.javaType()).readValue(node.get("payload"));
      if (!node.path("nil").booleanValue() && value == null)
        throw new IllegalArgumentException("Invalid value");
      count(value == null ? "null-hit" : "hit");
      return CacheResult.hit(value);
    } catch (Exception ex) {
      count("invalid");
      if (!config.evictInvalid()) throw failure("CACHE_DECODE_FAILED", ex);
      // Do not delete here: an intervening writer may already have replaced the invalid entry.
      return CacheResult.miss();
    }
  }

  public <T> T getOrLoad(CacheKey key, CacheType<T> type, Supplier<T> loader) {
    return getOrLoad(key, type, config.policy(), loader);
  }

  public <T> T getOrLoad(CacheKey key, CacheType<T> type, CachePolicy policy, Supplier<T> loader) {
    Objects.requireNonNull(loader);
    Objects.requireNonNull(type);
    requireLock(policy);
    try {
      return readThrough(key, type, policy, loader);
    } catch (CacheReadFailure ex) {
      // LockExecutor has already released the loading lock before we enter degraded loading.
      for (var suppressed : ex.getSuppressed()) ex.original.addSuppressed(suppressed);
      if (ex.getSuppressed().length != 0 || !policy.fallbackOnReadFailure()) throw ex.original;
      if (!fallback.tryAcquire()) throw failure("CACHE_FALLBACK_BUSY", null);
      try {
        count("fallback");
        return loader.get();
      } finally {
        fallback.release();
      }
    }
  }

  private <T> T readThrough(
      CacheKey key, CacheType<T> type, CachePolicy policy, Supplier<T> loader) {
    CacheResult<T> result = readForLoad(key, type);
    if (result.hit()) return result.value();
    if (!policy.protectLoads()) return load(key, type, policy, loader);
    boolean[] acquired = {false};
    try {
      return locks.execute(
          physicalKey(key),
          policy.loadWait(),
          () -> {
            acquired[0] = true;
            CacheResult<T> second = readForLoad(key, type);
            return second.hit() ? second.value() : load(key, type, policy, loader);
          });
    } catch (DistributedException ex) {
      // Do not mistake a loader's business exception for failure to acquire our loading lock.
      if (acquired[0] || !ex.getCode().equals("LOCK_BUSY")) throw ex;
      CacheResult<T> last = readForLoad(key, type);
      if (last.hit()) return last.value();
      throw failure("CACHE_LOAD_TIMEOUT", ex);
    }
  }

  private <T> CacheResult<T> readForLoad(CacheKey key, CacheType<T> type) {
    try {
      return get(key, type);
    } catch (DistributedException ex) {
      if (!ex.getCode().equals("CACHE_READ_FAILED")) throw ex;
      throw new CacheReadFailure(ex);
    }
  }

  private static final class CacheReadFailure extends RuntimeException {
    private final DistributedException original;

    CacheReadFailure(DistributedException original) {
      super(original);
      this.original = original;
    }
  }

  private <T> T load(CacheKey key, CacheType<T> type, CachePolicy policy, Supplier<T> loader) {
    requireCommittedRead();
    count("load");
    T value = loader.get(); // Exceptions propagate; never turn failures into negative entries.
    requireCommittedRead();
    String raw;
    try {
      ObjectNode node = mapper.createObjectNode();
      node.put("format", 1);
      node.put("type", type.javaType().toCanonical());
      node.put("nil", value == null);
      node.set("payload", mapper.valueToTree(value));
      raw = mapper.writeValueAsString(node);
      if (raw.getBytes(StandardCharsets.UTF_8).length > config.maxPayloadBytes())
        throw new IllegalArgumentException("Cache payload too large");
    } catch (Exception ex) {
      count("encode-failure");
      throw failure("CACHE_ENCODE_FAILED", ex);
    }
    try {
      redis
          .opsForValue()
          .set(physicalKey(key), raw, value == null ? policy.nullTtl() : policy.valueTtl());
    } catch (DataAccessException ex) {
      count("write-failure");
      LOG.warn("Cache write failed", ex);
    }
    return value;
  }

  private void requireCommittedRead() {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException(
          "Cache refill requires no active transaction; commit the loader transaction before"
              + " returning");
  }

  public void evict(CacheKey key) {
    try {
      redis.delete(physicalKey(key));
    } catch (DataAccessException ex) {
      count("evict-failure");
      throw failure("CACHE_EVICT_FAILED", ex);
    }
  }

  public void evictAfterCommit(CacheKey key) {
    Objects.requireNonNull(key);
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      evict(key);
      return;
    }
    if (!TransactionSynchronizationManager.isSynchronizationActive())
      throw new IllegalStateException("Active transaction requires synchronization");
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              evict(key);
            } catch (RuntimeException ex) {
              LOG.error("Cache invalidation failed after database commit", ex);
            }
          }
        });
  }
}
