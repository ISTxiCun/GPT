package com.istxicun.distributedtest;

import static org.assertj.core.api.Assertions.*;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.istxicun.backend.BackendApplication;
import com.istxicun.backend.common.api.ApiResponse;
import com.istxicun.backend.distributed.*;
import com.istxicun.backend.distributed.key.RedisKeyFactory;
import com.istxicun.backend.distributed.lock.LockExecutor;
import com.istxicun.backend.distributed.ratelimit.*;
import com.istxicun.backend.distributed.submission.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DistributedRedisIT {
  ConfigurableApplicationContext first, second;
  final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  final ObjectMapper json = new ObjectMapper();
  final String namespace = "it-" + UUID.randomUUID();
  final List<String> tokens = new ArrayList<>();

  @TestConfiguration(proxyBeanMethods = false)
  static class ProbeConfiguration {
    @Bean
    ProbeController probeController() {
      return new ProbeController();
    }
  }

  @RestController
  static class ProbeController {
    @PostMapping("/api/public/it/login")
    ApiResponse<Map<String, String>> login() {
      String user = "it-" + UUID.randomUUID();
      StpUtil.login(user);
      return ApiResponse.ok(Map.of("token", StpUtil.getTokenValue(), "user", user));
    }

    @GetMapping("/api/it/me")
    ApiResponse<String> me() {
      return ApiResponse.ok(StpUtil.getLoginIdAsString());
    }

    @PostMapping("/api/it/logout")
    ApiResponse<Void> logout() {
      StpUtil.logout();
      return ApiResponse.ok(null);
    }

    @RateLimit(key = "http-user", limit = 5, windowSeconds = 60)
    @GetMapping("/api/it/rate")
    ApiResponse<String> rate() {
      return ApiResponse.ok("accepted");
    }

    @RateLimit(
        key = "http-global",
        dimension = RateLimit.Dimension.GLOBAL,
        limit = 1,
        windowSeconds = 1)
    @GetMapping("/api/public/it/global")
    ApiResponse<String> global() {
      return ApiResponse.ok("accepted");
    }

    @RateLimit(key = "http-ip", dimension = RateLimit.Dimension.IP, limit = 1, windowSeconds = 60)
    @GetMapping("/api/public/it/ip")
    ApiResponse<String> ip() {
      return ApiResponse.ok("accepted");
    }

    @RateLimit(key = "public-user", limit = 1, windowSeconds = 60)
    @GetMapping("/api/public/it/user")
    ApiResponse<String> user() {
      return ApiResponse.ok("accepted");
    }

    @PreventDuplicateSubmission(windowSeconds = 1)
    @PostMapping("/api/it/submit")
    ApiResponse<String> submit() {
      return ApiResponse.ok("accepted");
    }

    @PreventDuplicateSubmission(windowSeconds = 30)
    @PostMapping("/api/it/failure")
    ApiResponse<String> failure() {
      throw new IllegalStateException("simulated-business-failure");
    }
  }

  ConfigurableApplicationContext start(String instance) {
    return new SpringApplicationBuilder(BackendApplication.class, ProbeConfiguration.class)
        .run(
            "--server.port=0",
            "--management.server.port=0",
            "--spring.datasource.url=jdbc:h2:mem:"
                + instance
                + namespace
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
            "--spring.datasource.driver-class-name=org.h2.Driver",
            "--spring.datasource.username=sa",
            "--spring.datasource.password=",
            "--spring.data.redis.host="
                + System.getenv().getOrDefault("TEST_REDIS_HOST", "127.0.0.1"),
            "--spring.data.redis.port=" + System.getenv().getOrDefault("TEST_REDIS_PORT", "6379"),
            "--spring.data.redis.password="
                + System.getenv().getOrDefault("TEST_REDIS_PASSWORD", ""),
            "--spring.data.redis.database="
                + System.getenv().getOrDefault("TEST_REDIS_DATABASE", "0"),
            "--app.identity.namespace=" + namespace,
            "--app.identity.environment=test",
            "--app.distributed.lock.enabled=true",
            "--app.distributed.lock.watchdog-millis=1000",
            "--app.distributed.rate-limit.enabled=true",
            "--app.distributed.submission.enabled=true",
            "--sa-token.timeout=60",
            "--spring.main.banner-mode=off",
            "--app.cors.allowed-origins=http://localhost:5173");
  }

  @BeforeAll
  void startInstances() {
    first = start("a");
    second = start("b");
  }

  int port(ConfigurableApplicationContext context) {
    return ((ServletWebServerApplicationContext) context).getWebServer().getPort();
  }

  HttpResponse<String> request(
      ConfigurableApplicationContext context, String method, String path, String token, String key)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port(context) + path))
            .timeout(Duration.ofSeconds(5))
            .method(method, HttpRequest.BodyPublishers.noBody());
    if (token != null) builder.header("Authorization", "Bearer " + token);
    if (key != null) builder.header("Idempotency-Key", key);
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  String login() throws Exception {
    var response = request(first, "POST", "/api/public/it/login", null, null);
    assertThat(response.statusCode()).isEqualTo(200);
    String token = json.readTree(response.body()).path("data").path("token").asText();
    tokens.add(token);
    return token;
  }

  @AfterAll
  void cleanup() throws Exception {
    try {
      if (first != null) {
        for (String token : tokens) request(first, "POST", "/api/it/logout", token, null);
        var redis = first.getBean(StringRedisTemplate.class);
        Set<String> keys = redis.keys(namespace + ":test:dist:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
      }
    } finally {
      if (second != null) second.close();
      if (first != null) first.close();
    }
  }

  @Test
  void tokenCreatedOnOneInstanceWorksOnTheOtherAndLogoutIsShared() throws Exception {
    String token = login();
    assertThat(request(second, "GET", "/api/it/me", token, null).statusCode()).isEqualTo(200);
    assertThat(request(first, "POST", "/api/it/logout", token, null).statusCode()).isEqualTo(200);
    assertThat(request(second, "GET", "/api/it/me", token, null).statusCode()).isEqualTo(401);
  }

  @Test
  void userRateBudgetSharedAcrossInstancesAndRetryAfterExposed() throws Exception {
    String token = login();
    for (int i = 0; i < 5; i++)
      assertThat(
              request(i % 2 == 0 ? first : second, "GET", "/api/it/rate", token, null).statusCode())
          .isEqualTo(200);
    var denied = request(second, "GET", "/api/it/rate", token, null);
    assertThat(denied.statusCode()).isEqualTo(429);
    assertThat(denied.headers().firstValue("Retry-After")).isPresent();
    assertThat(json.readTree(denied.body()).path("code").asText()).isEqualTo("RATE_LIMITED");
    assertThat(request(first, "GET", "/api/it/rate", login(), null).statusCode()).isEqualTo(200);
  }

  @Test
  void globalWindowResetsAndAnonymousUserDimensionRequiresLogin() throws Exception {
    assertThat(request(first, "GET", "/api/public/it/global", null, null).statusCode())
        .isEqualTo(200);
    assertThat(request(second, "GET", "/api/public/it/global", null, null).statusCode())
        .isEqualTo(429);
    Thread.sleep(1200);
    assertThat(request(second, "GET", "/api/public/it/global", null, null).statusCode())
        .isEqualTo(200);
    assertThat(request(first, "GET", "/api/public/it/user", null, null).statusCode())
        .isEqualTo(401);
  }

  @Test
  void forgedForwardedIpCannotBypassLimit() throws Exception {
    assertThat(request(first, "GET", "/api/public/it/ip", null, null).statusCode()).isEqualTo(200);
    var forged =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port(second) + "/api/public/it/ip"))
            .header("X-Forwarded-For", "203.0.113.99")
            .GET()
            .build();
    assertThat(http.send(forged, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(429);
  }

  @Test
  void submissionIsSharedExpiresAndDoesNotClearOnBusinessFailure() throws Exception {
    String token = login(), key = UUID.randomUUID().toString();
    assertThat(request(first, "POST", "/api/it/submit", token, null).statusCode()).isEqualTo(400);
    assertThat(request(first, "POST", "/api/it/submit", token, "short").statusCode())
        .isEqualTo(400);
    assertThat(request(first, "POST", "/api/it/submit", token, key).statusCode()).isEqualTo(200);
    assertThat(request(second, "POST", "/api/it/submit", token, key).statusCode()).isEqualTo(409);
    Thread.sleep(1200);
    assertThat(request(second, "POST", "/api/it/submit", token, key).statusCode()).isEqualTo(200);
    String failed = UUID.randomUUID().toString();
    assertThat(request(first, "POST", "/api/it/failure", token, failed).statusCode())
        .isEqualTo(500);
    assertThat(request(second, "POST", "/api/it/failure", token, failed).statusCode())
        .isEqualTo(409);
  }

  @Test
  void concurrentSubmissionsAdmitExactlyOne() throws Exception {
    String token = login(), key = UUID.randomUUID().toString();
    var barrier = new CyclicBarrier(8);
    var workers = Executors.newFixedThreadPool(8);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        var instance = i % 2 == 0 ? first : second;
        results.add(
            workers.submit(
                () -> {
                  barrier.await(3, TimeUnit.SECONDS);
                  return request(instance, "POST", "/api/it/submit", token, key).statusCode();
                }));
      }
      List<Integer> statuses = new ArrayList<>();
      for (var f : results) statuses.add(f.get(5, TimeUnit.SECONDS));
      assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
      assertThat(statuses).filteredOn(s -> s == 409).hasSize(7);
    } finally {
      workers.shutdownNow();
    }
  }

  @Test
  void lockMutualExclusionAcrossIndependentClients() throws Exception {
    var locks = List.of(first.getBean(LockExecutor.class), second.getBean(LockExecutor.class));
    var barrier = new CyclicBarrier(2);
    var workers = Executors.newFixedThreadPool(2);
    var active = new AtomicInteger();
    var maximum = new AtomicInteger();
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (var executor : locks)
        results.add(
            workers.submit(
                () -> {
                  barrier.await(3, TimeUnit.SECONDS);
                  return executor.execute(
                      "mutex",
                      Duration.ofSeconds(3),
                      () -> {
                        int now = active.incrementAndGet();
                        maximum.accumulateAndGet(now, Math::max);
                        try {
                          Thread.sleep(100);
                          return now;
                        } catch (InterruptedException ex) {
                          Thread.currentThread().interrupt();
                          throw new RuntimeException(ex);
                        } finally {
                          active.decrementAndGet();
                        }
                      });
                }));
      for (var f : results) assertThat(f.get(5, TimeUnit.SECONDS)).isEqualTo(1);
      assertThat(maximum).hasValue(1);
    } finally {
      workers.shutdownNow();
    }
  }

  @Test
  void watchdogRenewsAndCallbackFailureReleasesLock() {
    var a = first.getBean(LockExecutor.class);
    var b = second.getBean(LockExecutor.class);
    assertThatThrownBy(
            () ->
                a.execute(
                    "renewal",
                    Duration.ZERO,
                    () -> {
                      try {
                        Thread.sleep(1800);
                      } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                      }
                      assertThatThrownBy(() -> b.execute("renewal", Duration.ZERO, () -> "never"))
                          .isInstanceOfSatisfying(
                              DistributedException.class,
                              ex -> assertThat(ex.getCode()).isEqualTo("LOCK_BUSY"));
                      throw new IllegalArgumentException("business failure");
                    }))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("business failure");
    assertThat(b.execute("renewal", Duration.ZERO, () -> "released")).isEqualTo("released");
  }

  @Test
  void lockWaitInterruptionRestoresFlag() throws Exception {
    var lock =
        first
            .getBean(RedissonClient.class)
            .getLock(first.getBean(RedisKeyFactory.class).key("lock", "interrupt"));
    var entered = new CountDownLatch(1);
    var failure = new AtomicReference<Throwable>();
    var interrupted = new AtomicBoolean();
    Thread worker =
        new Thread(
            () -> {
              entered.countDown();
              try {
                second
                    .getBean(LockExecutor.class)
                    .execute("interrupt", Duration.ofSeconds(5), () -> "never");
              } catch (Throwable ex) {
                failure.set(ex);
                interrupted.set(Thread.currentThread().isInterrupted());
              }
            });
    lock.lock();
    try {
      worker.start();
      assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
      Thread.sleep(150);
      worker.interrupt();
      worker.join(3000);
      assertThat(worker.isAlive()).isFalse();
      assertThat(interrupted).isTrue();
      assertThat(failure.get())
          .isInstanceOfSatisfying(
              DistributedException.class,
              ex -> assertThat(ex.getCode()).isEqualTo("LOCK_INTERRUPTED"));
    } finally {
      lock.unlock();
      worker.interrupt();
      worker.join(1000);
    }
  }

  @Test
  void realConnectionFailureHonorsOpenClosedAndNeverBypassesSubmissionGuard() {
    var config = new RedisStandaloneConfiguration("127.0.0.1", 1);
    var factory =
        new LettuceConnectionFactory(
            config,
            LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(150))
                .shutdownTimeout(Duration.ZERO)
                .build());
    factory.afterPropertiesSet();
    factory.start();
    try {
      var template = new StringRedisTemplate(factory);
      var keys = first.getBean(RedisKeyFactory.class);
      var rate = new RedisRateLimiter(template, keys);
      assertThatThrownBy(
              () ->
                  rate.check(
                      "outage",
                      RateLimit.Dimension.GLOBAL,
                      "all",
                      1,
                      1,
                      RateLimit.FailurePolicy.CLOSED))
          .isInstanceOfSatisfying(
              DistributedException.class,
              ex ->
                  assertThat(ex.getCategory())
                      .isEqualTo(
                          com.istxicun.backend.common.exception.FailureCategory.UNAVAILABLE));
      assertThatCode(
              () ->
                  rate.check(
                      "outage",
                      RateLimit.Dimension.GLOBAL,
                      "all",
                      1,
                      1,
                      RateLimit.FailurePolicy.OPEN))
          .doesNotThrowAnyException();
      assertThatThrownBy(
              () ->
                  new SubmissionGuard(template, keys)
                      .acquire("user", "POST:/api/test", UUID.randomUUID().toString(), 5))
          .isInstanceOfSatisfying(
              DistributedException.class,
              ex ->
                  assertThat(ex.getCategory())
                      .isEqualTo(
                          com.istxicun.backend.common.exception.FailureCategory.UNAVAILABLE));
    } finally {
      factory.destroy();
    }
    var stopped = Redisson.create(first.getBean(RedissonClient.class).getConfig());
    stopped.shutdown();
    assertThatThrownBy(
            () ->
                new LockExecutor(stopped, first.getBean(RedisKeyFactory.class))
                    .execute("stopped", Duration.ZERO, () -> "never"))
        .isInstanceOfSatisfying(
            DistributedException.class,
            ex ->
                assertThat(ex.getCategory())
                    .isEqualTo(com.istxicun.backend.common.exception.FailureCategory.UNAVAILABLE));
  }
}
