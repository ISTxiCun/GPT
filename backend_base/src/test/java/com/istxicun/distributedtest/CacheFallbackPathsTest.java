package com.istxicun.distributedtest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.istxicun.backend.common.exception.FailureCategory;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.distributed.DistributedException;
import com.istxicun.backend.distributed.cache.*;
import com.istxicun.backend.distributed.key.RedisKeyFactory;
import com.istxicun.backend.distributed.lock.LockExecutor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.*;

class CacheFallbackPathsTest {
  enum Stage {
    FIRST,
    SECOND,
    LAST
  }

  final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  final ValueOperations<String, String> values = mock(ValueOperations.class);
  final LockExecutor locks = mock(LockExecutor.class);
  final AtomicBoolean held = new AtomicBoolean();
  final CacheKey key = new CacheKey("users", "v1", "1");

  CacheFallbackPathsTest() {
    when(redis.opsForValue()).thenReturn(values);
  }

  CacheClient client(boolean fallback) {
    return new CacheClient(
        redis,
        new RedisKeyFactory(new ApplicationIdentity("test", "fallback")),
        new ObjectMapper(),
        new CacheProperties(true, 1000, 100, 0, 10, true, fallback, 4096, 1, true),
        locks,
        new SimpleMeterRegistry());
  }

  void fault(Stage stage) {
    if (stage == Stage.FIRST)
      when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
    else
      when(values.get(anyString()))
          .thenReturn(null)
          .thenThrow(new RedisConnectionFailureException("offline"));
    if (stage == Stage.LAST)
      when(locks.execute(anyString(), any(Duration.class), any()))
          .thenThrow(new DistributedException(FailureCategory.CONFLICT, "LOCK_BUSY", "busy"));
    else
      doAnswer(
              i -> {
                held.set(true);
                try {
                  return ((Supplier<?>) i.getArgument(2)).get();
                } finally {
                  held.set(false);
                }
              })
          .when(locks)
          .execute(anyString(), any(Duration.class), any());
  }

  @ParameterizedTest
  @EnumSource(Stage.class)
  void eachRedisReadStageDegradesOnlyOnceAfterLockRelease(Stage stage) {
    fault(stage);
    var cache = client(true);
    var calls = new AtomicInteger();
    assertThat(
            cache.getOrLoad(
                key,
                CacheType.of(String.class),
                () -> {
                  assertThat(held).isFalse();
                  calls.incrementAndGet();
                  assertThatThrownBy(
                          () -> cache.getOrLoad(key, CacheType.of(String.class), () -> "nested"))
                      .isInstanceOfSatisfying(
                          DistributedException.class,
                          e -> assertThat(e.getCode()).isEqualTo("CACHE_FALLBACK_BUSY"));
                  return "loaded";
                }))
        .isEqualTo("loaded");
    assertThat(calls).hasValue(1);
  }

  @ParameterizedTest
  @EnumSource(Stage.class)
  void disabledFallbackKeepsReadFailures(Stage stage) {
    fault(stage);
    var calls = new AtomicInteger();
    assertThatThrownBy(
            () ->
                client(false)
                    .getOrLoad(
                        key,
                        CacheType.of(String.class),
                        () -> {
                          calls.incrementAndGet();
                          return "x";
                        }))
        .isInstanceOfSatisfying(
            DistributedException.class,
            e -> assertThat(e.getCode()).isEqualTo("CACHE_READ_FAILED"));
    assertThat(calls).hasValue(0);
    assertThat(held).isFalse();
  }

  @Test
  void loaderExceptionCodesNeverTriggerASecondLoadOrBusyRecheck() {
    doAnswer(i -> ((Supplier<?>) i.getArgument(2)).get())
        .when(locks)
        .execute(anyString(), any(Duration.class), any());
    var cache = client(true);
    for (var code : java.util.List.of("CACHE_READ_FAILED", "LOCK_BUSY")) {
      var failure = new DistributedException(FailureCategory.UNAVAILABLE, code, "business failure");
      var calls = new AtomicInteger();
      assertThatThrownBy(
              () ->
                  cache.getOrLoad(
                      key,
                      CacheType.of(String.class),
                      () -> {
                        calls.incrementAndGet();
                        throw failure;
                      }))
          .isSameAs(failure);
      assertThat(calls).hasValue(1);
    }
  }

  @Test
  void lockInfrastructureAndReleaseErrorsDoNotDegrade() {
    var cache = client(true);
    var lockFailure =
        new DistributedException(
            FailureCategory.UNAVAILABLE, "LOCK_RELEASE_FAILED", "release failed");
    when(values.get(anyString())).thenReturn(null);
    when(locks.execute(anyString(), any(Duration.class), any())).thenThrow(lockFailure);
    assertThatThrownBy(() -> cache.getOrLoad(key, CacheType.of(String.class), () -> "never"))
        .isSameAs(lockFailure);
    when(values.get(anyString()))
        .thenReturn(null)
        .thenThrow(new RedisConnectionFailureException("offline"));
    doAnswer(
            i -> {
              try {
                return ((Supplier<?>) i.getArgument(2)).get();
              } catch (RuntimeException ex) {
                ex.addSuppressed(lockFailure);
                throw ex;
              }
            })
        .when(locks)
        .execute(anyString(), any(Duration.class), any());
    assertThatThrownBy(
            () ->
                cache.getOrLoad(
                    key,
                    CacheType.of(String.class),
                    () -> {
                      throw new AssertionError("must not degrade after unlock failure");
                    }))
        .isInstanceOfSatisfying(
            DistributedException.class,
            e -> assertThat(e.getSuppressed()).containsExactly(lockFailure));
  }
}
