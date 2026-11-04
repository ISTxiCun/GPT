package com.istxicun.distributedtest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.distributed.cache.*;
import com.istxicun.backend.distributed.key.RedisKeyFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.*;
import org.springframework.transaction.support.*;

class CacheClientTest {
  final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  final ValueOperations<String, String> values = mock(ValueOperations.class);
  final CacheProperties config =
      new CacheProperties(true, 1000, 100, 100, 30, false, false, 4096, 1, true);
  final CacheClient cache =
      new CacheClient(
          redis,
          new RedisKeyFactory(new ApplicationIdentity("test", "a")),
          new ObjectMapper(),
          config,
          null,
          new SimpleMeterRegistry());
  final CacheKey key = new CacheKey("users", "v1", "tenant:123");
  final Map<String, String> store = new HashMap<>();

  CacheClientTest() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenAnswer(i -> store.get(i.getArgument(0)));
    doAnswer(
            i -> {
              store.put(i.getArgument(0), i.getArgument(1));
              return null;
            })
        .when(values)
        .set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void genericAndNegativeValuesRoundTrip() {
    var type = CacheType.of(new TypeReference<List<Map<String, Integer>>>() {});
    var expected = List.of(Map.of("count", 7));
    assertThat(cache.getOrLoad(key, type, () -> expected)).isEqualTo(expected);
    assertThat(cache.get(key, type).value().get(0).get("count")).isEqualTo(7);
    store.clear();
    cache.getOrLoad(key, type, List::of);
    assertThat(cache.get(key, type).state()).isEqualTo(CacheResult.State.VALUE);
    store.clear();
    AtomicInteger calls = new AtomicInteger();
    cache.getOrLoad(
        key,
        type,
        () -> {
          calls.incrementAndGet();
          return null;
        });
    cache.getOrLoad(
        key,
        type,
        () -> {
          calls.incrementAndGet();
          return expected;
        });
    assertThat(calls).hasValue(1);
    assertThat(cache.get(key, type).state()).isEqualTo(CacheResult.State.NULL);
  }

  @Test
  void loaderErrorsNeverWriteAndInvalidFormatsMiss() {
    assertThatThrownBy(
            () ->
                cache.getOrLoad(
                    key,
                    CacheType.of(String.class),
                    () -> {
                      throw new IllegalStateException("loader");
                    }))
        .hasMessage("loader");
    assertThat(store).isEmpty();
    store.put(cache.physicalKey(key), "{\"format\":99}");
    assertThat(cache.get(key, CacheType.of(String.class)).hit()).isFalse();
  }

  @Test
  void fallbackIsExplicitAndBounded() {
    when(values.get(anyString())).thenThrow(new DataAccessResourceFailureException("offline"));
    assertThatThrownBy(() -> cache.getOrLoad(key, CacheType.of(String.class), () -> "ok"))
        .hasMessageContaining("Cache");
    var policy =
        new CachePolicy(
            Duration.ofSeconds(1),
            Duration.ofMillis(100),
            Duration.ZERO,
            Duration.ZERO,
            false,
            true);
    assertThat(
            cache.getOrLoad(
                key,
                CacheType.of(String.class),
                policy,
                () -> {
                  assertThatThrownBy(
                          () ->
                              cache.getOrLoad(
                                  key, CacheType.of(String.class), policy, () -> "nested"))
                      .hasMessageContaining("Cache");
                  return "ok";
                }))
        .isEqualTo("ok");
  }

  @Test
  void afterCommitOnlyAndCommittedFailureDoesNotEscape() {
    TransactionSynchronizationManager.setActualTransactionActive(true);
    TransactionSynchronizationManager.initSynchronization();
    try {
      cache.evictAfterCommit(key);
      verify(redis, never()).delete(anyString());
      var sync = TransactionSynchronizationManager.getSynchronizations().get(0);
      sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
      verify(redis, never()).delete(anyString());
      when(redis.delete(anyString())).thenThrow(new DataAccessResourceFailureException("offline"));
      assertThatCode(sync::afterCommit).doesNotThrowAnyException();
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
  }

  @Test
  void ttlBoundariesAndEnvironmentIsolation() {
    for (int i = 0; i < 100; i++) {
      var k = new CacheKey("users", "v1", "" + i);
      cache.getOrLoad(k, CacheType.of(String.class), () -> "x");
    }
    verify(values, times(100))
        .set(
            anyString(),
            anyString(),
            argThat((Duration d) -> d.toMillis() >= 900 && d.toMillis() <= 1100));
    assertThatThrownBy(
            () ->
                new CachePolicy(
                    Duration.ofMillis(1),
                    Duration.ofSeconds(1),
                    Duration.ofMillis(1),
                    Duration.ZERO,
                    false,
                    false))
        .isInstanceOf(IllegalArgumentException.class);
    var other = new RedisKeyFactory(new ApplicationIdentity("test", "b"));
    assertThat(other.key("cache", "users", "v1", "tenant:123"))
        .isNotEqualTo(cache.physicalKey(key));
  }
}
