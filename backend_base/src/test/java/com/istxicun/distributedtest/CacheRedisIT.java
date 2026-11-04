package com.istxicun.distributedtest;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.distributed.cache.*;
import com.istxicun.backend.distributed.key.RedisKeyFactory;
import com.istxicun.backend.distributed.lock.LockExecutor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class CacheRedisIT {
  @Test
  void twoClientsSerializeLoadsExpireNullsAndReproduceStaleRefill() throws Exception {
    String host = System.getenv().getOrDefault("TEST_REDIS_HOST", "127.0.0.1");
    int port = Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_PORT", "6379"));
    var standalone =
        new org.springframework.data.redis.connection.RedisStandaloneConfiguration(host, port);
    standalone.setPassword(System.getenv().getOrDefault("TEST_REDIS_PASSWORD", ""));
    standalone.setDatabase(
        Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_DATABASE", "0")));
    var factory = new LettuceConnectionFactory(standalone);
    factory.afterPropertiesSet();
    factory.start();
    var redis = new StringRedisTemplate(factory);
    var cfg = new Config();
    cfg.setThreads(2).setNettyThreads(2);
    var server =
        cfg.useSingleServer()
            .setAddress("redis://" + host + ":" + port)
            .setDatabase(standalone.getDatabase());
    if (standalone.getPassword().isPresent())
      server.setPassword(new String(standalone.getPassword().get()));
    var a = Redisson.create(cfg);
    var b = Redisson.create(cfg);
    var keys =
        new RedisKeyFactory(new ApplicationIdentity("cache-it-" + UUID.randomUUID(), "test"));
    var props = new CacheProperties(true, 1000, 100, 0, 300, true, false, 4096, 1, true);
    var first =
        new CacheClient(
            redis,
            keys,
            new ObjectMapper(),
            props,
            new LockExecutor(a, keys),
            new SimpleMeterRegistry());
    var second =
        new CacheClient(
            redis,
            keys,
            new ObjectMapper(),
            props,
            new LockExecutor(b, keys),
            new SimpleMeterRegistry());
    var key = new CacheKey("test", "v1", "hot");
    var type = CacheType.of(String.class);
    var pool = Executors.newFixedThreadPool(2);
    try {
      first.get(key, type);
      new LockExecutor(a, keys).execute("warmup", Duration.ofSeconds(3), () -> null);
      new LockExecutor(b, keys).execute("warmup", Duration.ofSeconds(3), () -> null);
      var entered = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      AtomicInteger loads = new AtomicInteger();
      var load =
          pool.submit(
              () ->
                  first.getOrLoad(
                      key,
                      type,
                      () -> {
                        loads.incrementAndGet();
                        entered.countDown();
                        await(release);
                        return "old";
                      }));
      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> second.getOrLoad(key, type, () -> "unexpected"))
          .hasMessageContaining("Cache");
      var waiting =
          pool.submit(
              () ->
                  second.getOrLoad(
                      key,
                      type,
                      () -> {
                        loads.incrementAndGet();
                        return "duplicate";
                      }));
      release.countDown();
      assertThat(load.get(3, TimeUnit.SECONDS)).isEqualTo("old");
      assertThat(waiting.get(3, TimeUnit.SECONDS)).isEqualTo("old");
      assertThat(loads).hasValue(1);
      first.evict(key);
      first.getOrLoad(key, type, () -> null);
      assertThat(second.get(key, type).state()).isEqualTo(CacheResult.State.NULL);
      Thread.sleep(180);
      assertThat(second.get(key, type).hit()).isFalse();
      // A database update + successful invalidation cannot prevent an already running old read
      // refill.
      var captured = new CountDownLatch(1);
      var refill = new CountDownLatch(1);
      var stale =
          pool.submit(
              () ->
                  first.getOrLoad(
                      key,
                      type,
                      () -> {
                        captured.countDown();
                        await(refill);
                        return "old snapshot";
                      }));
      assertThat(captured.await(3, TimeUnit.SECONDS)).isTrue();
      second.evict(key);
      refill.countDown();
      stale.get(3, TimeUnit.SECONDS);
      assertThat(second.get(key, type).value()).isEqualTo("old snapshot");
      first.evict(key);
      var ready = java.nio.file.Files.createTempFile("cache-loader", ".ready");
      java.nio.file.Files.delete(ready);
      var child =
          new ProcessBuilder(
                  System.getProperty("java.home") + "/bin/java",
                  "-cp",
                  System.getProperty(
                      "surefire.test.class.path", System.getProperty("java.class.path")),
                  CacheRedisIT.class.getName(),
                  host,
                  Integer.toString(port),
                  System.getenv().getOrDefault("TEST_REDIS_PASSWORD", ""),
                  Integer.toString(standalone.getDatabase()),
                  keys.key("lock", first.physicalKey(key)),
                  ready.toString())
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      try {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!java.nio.file.Files.exists(ready)
            && System.nanoTime() < deadline
            && child.isAlive()) Thread.sleep(20);
        assertThat(java.nio.file.Files.exists(ready)).isTrue();
        child.destroyForcibly().waitFor(3, TimeUnit.SECONDS);
        Thread.sleep(1600);
        assertThat(second.getOrLoad(key, type, () -> "recovered")).isEqualTo("recovered");
      } finally {
        child.destroyForcibly();
        java.nio.file.Files.deleteIfExists(ready);
      }

    } finally {
      pool.shutdownNow();
      first.evict(key);
      a.shutdown();
      b.shutdown();
      factory.destroy();
    }
  }

  /** Child process used to verify watchdog recovery after an abrupt loader death. */
  public static void main(String[] args) throws Exception {
    var cfg = new Config();
    cfg.setThreads(2).setNettyThreads(2);
    cfg.setLockWatchdogTimeout(1000);
    var server =
        cfg.useSingleServer()
            .setAddress("redis://" + args[0] + ":" + args[1])
            .setDatabase(Integer.parseInt(args[3]));
    if (!args[2].isEmpty()) server.setPassword(args[2]);
    var client = Redisson.create(cfg);
    var lock = client.getLock(args[4]);
    lock.lock();
    java.nio.file.Files.writeString(java.nio.file.Path.of(args[5]), "ready");
    Thread.sleep(60000);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }
}
