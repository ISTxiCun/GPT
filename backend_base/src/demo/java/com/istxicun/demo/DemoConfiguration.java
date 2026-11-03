package com.istxicun.demo;

import cn.dev33.satoken.stp.StpUtil;
import com.istxicun.backend.common.api.ApiResponse;
import com.istxicun.backend.common.exception.FailureCategory;
import com.istxicun.backend.distributed.DistributedException;
import com.istxicun.backend.distributed.key.RedisKeyFactory;
import com.istxicun.backend.distributed.lock.LockExecutor;
import com.istxicun.backend.distributed.ratelimit.RateLimit;
import com.istxicun.backend.distributed.submission.PreventDuplicateSubmission;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@Configuration(proxyBeanMethods = false)
@Profile("distributed-demo")
public class DemoConfiguration {
  @RestController
  static class DemoController {
    private final LockExecutor locks;
    private final StringRedisTemplate redis;
    private final RedisKeyFactory keys;
    private final String instance;
    private final byte[] accessKey;

    DemoController(
        LockExecutor locks,
        StringRedisTemplate redis,
        RedisKeyFactory keys,
        @Value("${demo.instance-id}") String instance,
        @Value("${demo.access-key}") String accessKey) {
      if (accessKey.isBlank())
        throw new IllegalArgumentException("DEMO_ACCESS_KEY must not be blank");
      this.locks = locks;
      this.redis = redis;
      this.keys = keys;
      this.instance = instance;
      this.accessKey = accessKey.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/api/public/demo/login")
    ApiResponse<Map<String, String>> login(@RequestHeader("X-Demo-Key") String supplied) {
      if (!MessageDigest.isEqual(accessKey, supplied.getBytes(StandardCharsets.UTF_8)))
        throw new DistributedException(FailureCategory.FORBIDDEN, "DEMO_ACCESS_DENIED", "演示密钥不正确");
      String user = "demo-" + UUID.randomUUID();
      StpUtil.login(user);
      return ApiResponse.ok(
          Map.of("token", StpUtil.getTokenValue(), "user", user, "instance", instance));
    }

    @GetMapping("/api/demo/me")
    ApiResponse<Map<String, String>> me() {
      return state();
    }

    @PostMapping("/api/demo/logout")
    ApiResponse<Void> logout() {
      StpUtil.logout();
      return ApiResponse.ok(null);
    }

    @RateLimit(key = "demo-rate", limit = 5, windowSeconds = 60)
    @GetMapping("/api/demo/rate")
    ApiResponse<Map<String, String>> rate() {
      return state();
    }

    @PreventDuplicateSubmission(windowSeconds = 10)
    @PostMapping("/api/demo/submit")
    ApiResponse<Map<String, String>> submit() {
      return state();
    }

    @PostMapping("/api/demo/lock")
    ApiResponse<Map<String, String>> lock(
        @RequestParam @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String resource,
        @RequestParam(defaultValue = "1000") @Min(0) @Max(3000) long holdMillis) {
      return locks.execute(
          "demo:" + resource,
          Duration.ofMillis(100),
          () -> {
            String activity = keys.key("demo-active", resource);
            Long active = redis.opsForValue().increment(activity);
            redis.expire(activity, Duration.ofSeconds(10));
            try {
              if (active == null || active != 1)
                throw new IllegalStateException("Mutual exclusion violated");
              try {
                Thread.sleep(holdMillis);
              } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw DistributedException.unavailable(ex);
              }
              return state();
            } finally {
              redis.opsForValue().decrement(activity);
            }
          });
    }

    private ApiResponse<Map<String, String>> state() {
      return ApiResponse.ok(Map.of("user", StpUtil.getLoginIdAsString(), "instance", instance));
    }
  }
}
