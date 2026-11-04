package com.istxicun.distributedtest;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.distributed.cache.*;
import com.istxicun.backend.distributed.key.RedisKeyFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit opt-in: mvn -Predis-it -Dit.test=CacheMysqlIT verify */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "TEST_MYSQL_URL",
    matches = ".+")
class CacheMysqlIT {
  @Test
  void committedUpdateEvictsAndRollbackKeepsCache() {
    var source =
        new DriverManagerDataSource(
            System.getenv()
                .getOrDefault(
                    "TEST_MYSQL_URL",
                    "jdbc:mysql://127.0.0.1:3306/cache_test?allowPublicKeyRetrieval=true&useSSL=false"),
            System.getenv().getOrDefault("TEST_MYSQL_USER", "root"),
            System.getenv().getOrDefault("TEST_MYSQL_PASSWORD", "cache-test"));
    var jdbc = new JdbcTemplate(source);
    String table = "cache_it_" + UUID.randomUUID().toString().replace("-", "");
    var standalone =
        new RedisStandaloneConfiguration(
            System.getenv().getOrDefault("TEST_REDIS_HOST", "127.0.0.1"),
            Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_PORT", "6379")));
    standalone.setPassword(System.getenv().getOrDefault("TEST_REDIS_PASSWORD", ""));
    var factory = new LettuceConnectionFactory(standalone);
    factory.afterPropertiesSet();
    factory.start();
    var cache =
        new CacheClient(
            new StringRedisTemplate(factory),
            new RedisKeyFactory(new ApplicationIdentity(table, "test")),
            new ObjectMapper(),
            new CacheProperties(true, 10000, 100, 0, 0, false, false, 4096, 1, true),
            null,
            new SimpleMeterRegistry());
    var key = new CacheKey("products", "v1", "1");
    var type = CacheType.of(String.class);
    var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    try {
      jdbc.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY, val VARCHAR(32))");
      jdbc.update("INSERT INTO " + table + " VALUES (1, 'old')");
      cache.getOrLoad(
          key,
          type,
          () -> jdbc.queryForObject("SELECT val FROM " + table + " WHERE id=1", String.class));
      tx.executeWithoutResult(
          status -> {
            jdbc.update("UPDATE " + table + " SET val='rolled back' WHERE id=1");
            cache.evictAfterCommit(key);
            status.setRollbackOnly();
          });
      assertThat(cache.get(key, type).value()).isEqualTo("old");
      assertThat(jdbc.queryForObject("SELECT val FROM " + table + " WHERE id=1", String.class))
          .isEqualTo("old");
      tx.executeWithoutResult(
          status -> {
            jdbc.update("UPDATE " + table + " SET val='new' WHERE id=1");
            cache.evictAfterCommit(key);
            assertThat(cache.get(key, type).hit()).isTrue();
          });
      assertThat(cache.get(key, type).hit()).isFalse();
      assertThat(
              cache.getOrLoad(
                  key,
                  type,
                  () ->
                      jdbc.queryForObject(
                          "SELECT val FROM " + table + " WHERE id=1", String.class)))
          .isEqualTo("new");
      cache.evict(key);
      assertThatThrownBy(
              () ->
                  tx.executeWithoutResult(
                      status -> {
                        jdbc.update("UPDATE " + table + " SET val='dirty' WHERE id=1");
                        cache.getOrLoad(
                            key,
                            type,
                            () ->
                                jdbc.queryForObject(
                                    "SELECT val FROM " + table + " WHERE id=1", String.class));
                      }))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cache refill");
      assertThat(jdbc.queryForObject("SELECT val FROM " + table + " WHERE id=1", String.class))
          .isEqualTo("new");
      assertThat(cache.get(key, type).hit()).isFalse();
      assertThat(
              cache.getOrLoad(
                  key,
                  type,
                  () ->
                      tx.execute(
                          status ->
                              jdbc.queryForObject(
                                  "SELECT val FROM " + table + " WHERE id=1", String.class))))
          .isEqualTo("new");
      assertThat(cache.get(key, type).value()).isEqualTo("new");
    } finally {
      cache.evict(key);
      jdbc.execute("DROP TABLE IF EXISTS " + table);
      factory.destroy();
    }
  }
}
