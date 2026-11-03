package com.istxicun.backend.distributed.ratelimit;

import com.istxicun.backend.distributed.DistributedException;
import com.istxicun.backend.distributed.key.RedisKeyFactory;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;

public final class RedisRateLimiter {
  private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
  private static final DefaultRedisScript<List> SCRIPT = script();

  private static DefaultRedisScript<List> script() {
    var script = new DefaultRedisScript<List>();
    script.setLocation(new ClassPathResource("redis/fixed-window.lua"));
    script.setResultType(List.class);
    return script;
  }

  private final StringRedisTemplate redis;
  private final RedisKeyFactory keys;

  public RedisRateLimiter(StringRedisTemplate redis, RedisKeyFactory keys) {
    this.redis = redis;
    this.keys = keys;
  }

  public void check(
      String name,
      RateLimit.Dimension dimension,
      String subject,
      long limit,
      long windowSeconds,
      RateLimit.FailurePolicy failurePolicy) {
    validate(name, limit, windowSeconds);
    List<?> result;
    try {
      result =
          redis.execute(
              SCRIPT,
              List.of(keys.key("rate", name, dimension.name(), subject)),
              Long.toString(limit),
              Long.toString(windowSeconds * 1000));
    } catch (DataAccessException ex) {
      if (failurePolicy == RateLimit.FailurePolicy.OPEN) {
        log.warn(
            "Rate limit backend unavailable; explicitly configured OPEN policy allows request");
        return;
      }
      throw DistributedException.unavailable(ex);
    }
    if (result == null || result.size() != 2)
      throw DistributedException.unavailable(
          new IllegalStateException("Invalid rate limit script response"));
    if (((Number) result.get(0)).longValue() == 0)
      throw new DistributedException(
          HttpStatus.TOO_MANY_REQUESTS,
          "RATE_LIMITED",
          "请求过于频繁",
          Math.max(1, ((Number) result.get(1)).longValue()),
          null);
  }

  public static void validate(String name, long limit, long windowSeconds) {
    if (name == null
        || !name.matches("[A-Za-z0-9][A-Za-z0-9:._-]{0,127}")
        || limit < 1
        || limit > 1000000
        || windowSeconds < 1
        || windowSeconds > 86400)
      throw new IllegalArgumentException(
          "RateLimit requires a valid key, limit 1..1000000 and windowSeconds 1..86400");
  }
}
