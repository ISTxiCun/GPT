package com.istxicun.backend.distributed.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.istxicun.backend.distributed.DistributedProperties;
import com.istxicun.backend.distributed.key.RedisKeyFactory;
import com.istxicun.backend.distributed.lock.LockExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
public class CacheConfiguration {
  @Bean
  @ConditionalOnProperty(prefix = "app.distributed.cache", name = "enabled", havingValue = "true")
  CacheClient cacheClient(
      StringRedisTemplate redis,
      RedisKeyFactory keys,
      ObjectMapper mapper,
      DistributedProperties properties,
      ObjectProvider<LockExecutor> locks,
      MeterRegistry metrics) {
    return new CacheClient(
        redis, keys, mapper, properties.cache(), locks.getIfAvailable(), metrics);
  }
}
