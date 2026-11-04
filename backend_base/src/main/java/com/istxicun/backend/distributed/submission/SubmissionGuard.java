package com.istxicun.backend.distributed.submission;

import com.istxicun.backend.common.exception.FailureCategory;
import com.istxicun.backend.distributed.DistributedException;
import com.istxicun.backend.distributed.key.RedisKeyFactory;
import java.time.Duration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Short-window admission guard, not a durable business idempotency record. */
public final class SubmissionGuard {
  private final StringRedisTemplate redis;
  private final RedisKeyFactory keys;
  private final io.micrometer.core.instrument.MeterRegistry metrics;

  public SubmissionGuard(StringRedisTemplate redis, RedisKeyFactory keys) {
    this(redis, keys, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
  }

  public SubmissionGuard(
      StringRedisTemplate redis,
      RedisKeyFactory keys,
      io.micrometer.core.instrument.MeterRegistry metrics) {
    this.redis = redis;
    this.keys = keys;
    this.metrics = metrics;
  }

  private void count(String result) {
    metrics.counter("distributed.submission", "result", result).increment();
  }

  public void acquire(String userId, String endpoint, String requestKey, long windowSeconds) {
    validateWindow(windowSeconds);
    if (requestKey == null || !requestKey.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}"))
      throw new DistributedException(
          FailureCategory.INVALID_ARGUMENT,
          "INVALID_IDEMPOTENCY_KEY",
          "请提供 8 至 128 位 Idempotency-Key");
    Boolean accepted;
    try {
      accepted =
          redis
              .opsForValue()
              .setIfAbsent(
                  keys.key("submission", userId, endpoint, requestKey),
                  "1",
                  Duration.ofSeconds(windowSeconds));
    } catch (DataAccessException ex) {
      count("backend-failure");
      throw DistributedException.unavailable(ex);
    }
    if (accepted == null) {
      count("backend-failure");
      throw DistributedException.unavailable(
          new IllegalStateException("No submission guard result"));
    }
    if (!accepted) {
      count("rejected");
      throw new DistributedException(FailureCategory.CONFLICT, "DUPLICATE_SUBMISSION", "请勿重复提交");
    }
    count("accepted");
    // Deliberately retained on both success and failure until TTL expires. No result replay.
  }

  public static void validateWindow(long seconds) {
    if (seconds < 1 || seconds > 86400)
      throw new IllegalArgumentException("Submission windowSeconds must be 1..86400");
  }
}
