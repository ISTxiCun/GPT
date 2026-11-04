package com.istxicun.distributedtest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.distributed.cache.*;
import com.istxicun.backend.distributed.key.RedisKeyFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.*;

class CacheTransactionTest {
  @Test
  void transactionRollbackCannotPublishDirtyValueAndCommittedLoaderCanCache() {
    var source =
        new DriverManagerDataSource(
            "jdbc:h2:mem:cache_tx_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    var jdbc = new JdbcTemplate(source);
    jdbc.execute("CREATE TABLE item(id INT PRIMARY KEY,val VARCHAR(32))");
    jdbc.update("INSERT INTO item VALUES(1,'committed')");
    var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    var redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    Map<String, String> entries = new HashMap<>();
    when(values.get(anyString())).thenAnswer(i -> entries.get(i.getArgument(0)));
    doAnswer(
            i -> {
              entries.put(i.getArgument(0), i.getArgument(1));
              return null;
            })
        .when(values)
        .set(anyString(), anyString(), any(Duration.class));
    var cache =
        new CacheClient(
            redis,
            new RedisKeyFactory(new ApplicationIdentity("test", "tx")),
            new ObjectMapper(),
            new CacheProperties(true, 10000, 100, 0, 10, false, false, 4096, 1, true),
            null,
            new SimpleMeterRegistry());
    var key = new CacheKey("item", "v1", "1");
    var type = CacheType.of(String.class);
    var loads = new AtomicInteger();
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    status -> {
                      jdbc.update("UPDATE item SET val='dirty' WHERE id=1");
                      cache.getOrLoad(
                          key,
                          type,
                          () -> {
                            loads.incrementAndGet();
                            return jdbc.queryForObject(
                                "SELECT val FROM item WHERE id=1", String.class);
                          });
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cache refill");
    assertThat(loads).hasValue(0);
    assertThat(entries).isEmpty();
    assertThat(jdbc.queryForObject("SELECT val FROM item WHERE id=1", String.class))
        .isEqualTo("committed");
    // Start and finish the read transaction inside the loader, before publication to Redis.
    assertThat(
            cache.getOrLoad(
                key,
                type,
                () ->
                    tx.execute(
                        s -> jdbc.queryForObject("SELECT val FROM item WHERE id=1", String.class))))
        .isEqualTo("committed");
    assertThat(cache.get(key, type).value()).isEqualTo("committed");
    assertThat(tx.<String>execute(s -> cache.getOrLoad(key, type, () -> "should not load")))
        .isEqualTo("committed");
  }

  @Test
  void loaderCannotLeaveAnUncommittedTransactionForCachePublication() {
    var redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    var cache =
        new CacheClient(
            redis,
            new RedisKeyFactory(new ApplicationIdentity("test", "tx")),
            new ObjectMapper(),
            new CacheProperties(true, 10000, 100, 0, 10, false, false, 4096, 1, true),
            null,
            new SimpleMeterRegistry());
    try {
      assertThatThrownBy(
              () ->
                  cache.getOrLoad(
                      new CacheKey("item", "v1", "1"),
                      CacheType.of(String.class),
                      () -> {
                        TransactionSynchronizationManager.setActualTransactionActive(true);
                        return "dirty";
                      }))
          .isInstanceOf(IllegalStateException.class);
      verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
  }
}
