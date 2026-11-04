package com.istxicun.distributedtest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.distributed.cache.*;
import com.istxicun.backend.distributed.key.RedisKeyFactory;
import com.istxicun.backend.distributed.lock.LockExecutor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.config.Config;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.*;

class CacheReadFallbackIT {
  @Test
  void lockedReadFailureReleasesRealRedisLockBeforeDegradedLoad() {
    String host = System.getenv().getOrDefault("TEST_REDIS_HOST", "127.0.0.1");
    int port = Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_PORT", "6379"));
    String password = System.getenv().getOrDefault("TEST_REDIS_PASSWORD", "");
    int database = Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_DATABASE", "0"));
    var standalone = new RedisStandaloneConfiguration(host, port);
    standalone.setPassword(password);
    standalone.setDatabase(database);
    var factory = new LettuceConnectionFactory(standalone);
    factory.afterPropertiesSet();
    factory.start();
    var actual = new StringRedisTemplate(factory);
    var redis = spy(actual);
    var delegate = actual.opsForValue();
    var reads = new AtomicInteger();
    ValueOperations<String, String> values =
        (ValueOperations<String, String>)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {ValueOperations.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("get") && reads.incrementAndGet() == 2)
                    throw new RedisConnectionFailureException("injected second read fault");
                  try {
                    return method.invoke(delegate, args);
                  } catch (InvocationTargetException ex) {
                    throw ex.getCause();
                  }
                });
    doReturn(values).when(redis).opsForValue();
    var config = new Config();
    config.setThreads(2).setNettyThreads(2);
    var server =
        config.useSingleServer().setAddress("redis://" + host + ":" + port).setDatabase(database);
    if (!password.isEmpty()) server.setPassword(password);
    var client = Redisson.create(config);
    var keys =
        new RedisKeyFactory(new ApplicationIdentity("fallback-it-" + UUID.randomUUID(), "test"));
    var key = new CacheKey("item", "v1", "1");
    var locks = new LockExecutor(client, keys);
    var cache =
        new CacheClient(
            redis,
            keys,
            new ObjectMapper(),
            new CacheProperties(true, 10000, 100, 0, 3000, true, true, 4096, 1, true),
            locks,
            new SimpleMeterRegistry());
    var lock = client.getLock(keys.key("lock", cache.physicalKey(key)));
    try {
      assertThat(
              cache.getOrLoad(
                  key,
                  CacheType.of(String.class),
                  () -> {
                    assertThat(lock.isLocked()).isFalse();
                    return "source";
                  }))
          .isEqualTo("source");
      assertThat(reads).hasValue(2);
      assertThat(actual.hasKey(cache.physicalKey(key))).isFalse();
      assertThat(lock.isLocked()).isFalse();
    } finally {
      actual.delete(cache.physicalKey(key));
      client.shutdown();
      factory.destroy();
    }
  }
}
