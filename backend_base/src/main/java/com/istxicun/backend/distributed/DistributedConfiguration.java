package com.istxicun.backend.distributed;

import com.istxicun.backend.distributed.key.RedisKeyFactory;
import com.istxicun.backend.distributed.lock.LockExecutor;
import com.istxicun.backend.distributed.ratelimit.RedisRateLimiter;
import com.istxicun.backend.distributed.submission.SubmissionGuard;
import com.istxicun.backend.distributed.web.ClientIpResolver;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DistributedProperties.class)
public class DistributedConfiguration {
  @Bean
  RedisKeyFactory redisKeyFactory(DistributedProperties properties) {
    return new RedisKeyFactory(properties);
  }

  @Bean
  ClientIpResolver clientIpResolver(DistributedProperties properties) {
    return new ClientIpResolver(properties.trustedProxies());
  }

  @Bean(destroyMethod = "shutdown")
  @ConditionalOnProperty(prefix = "app.distributed.lock", name = "enabled", havingValue = "true")
  RedissonClient distributedRedisson(RedisProperties redis, DistributedProperties properties) {
    if (redis.getUrl() != null || redis.getSentinel() != null || redis.getCluster() != null)
      throw new IllegalArgumentException(
          "V1 lock supports standalone Redis host/port only; URL/Sentinel/Cluster need an explicit"
              + " adapter");
    Config config = new Config();
    config
        .setThreads(2)
        .setNettyThreads(2)
        .setLockWatchdogTimeout(properties.lock().watchdogMillis());
    String host = redis.getHost().contains(":") ? "[" + redis.getHost() + "]" : redis.getHost();
    var single =
        config
            .useSingleServer()
            .setAddress(
                (redis.getSsl().isEnabled() ? "rediss://" : "redis://")
                    + host
                    + ":"
                    + redis.getPort())
            .setDatabase(redis.getDatabase())
            .setConnectionMinimumIdleSize(1)
            .setConnectionPoolSize(8)
            .setSubscriptionConnectionMinimumIdleSize(1)
            .setSubscriptionConnectionPoolSize(4)
            .setTimeout(
                redis.getTimeout() == null ? 3000 : Math.toIntExact(redis.getTimeout().toMillis()))
            .setConnectTimeout(
                redis.getConnectTimeout() == null
                    ? 3000
                    : Math.toIntExact(redis.getConnectTimeout().toMillis()));
    if (redis.getUsername() != null && !redis.getUsername().isBlank())
      single.setUsername(redis.getUsername());
    if (redis.getPassword() != null && !redis.getPassword().isEmpty())
      single.setPassword(redis.getPassword());
    return Redisson.create(config);
  }

  @Bean
  @ConditionalOnProperty(prefix = "app.distributed.lock", name = "enabled", havingValue = "true")
  LockExecutor lockExecutor(RedissonClient distributedRedisson, RedisKeyFactory keys) {
    return new LockExecutor(distributedRedisson, keys);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "app.distributed.rate-limit",
      name = "enabled",
      havingValue = "true")
  RedisRateLimiter redisRateLimiter(StringRedisTemplate redis, RedisKeyFactory keys) {
    return new RedisRateLimiter(redis, keys);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "app.distributed.submission",
      name = "enabled",
      havingValue = "true")
  SubmissionGuard submissionGuard(StringRedisTemplate redis, RedisKeyFactory keys) {
    return new SubmissionGuard(redis, keys);
  }
}
