package com.istxicun.backend.distributed.cache;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.distributed.cache", ignoreUnknownFields = false)
@Validated
public record CacheProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("300000") long ttlMillis,
    @DefaultValue("10000") long nullTtlMillis,
    @DefaultValue("30000") long jitterMillis,
    @DefaultValue("1000") long loadWaitMillis,
    @DefaultValue("false") boolean protectLoads,
    @DefaultValue("false") boolean fallbackOnReadFailure,
    @DefaultValue("1048576") @Min(128) @Max(16777216) int maxPayloadBytes,
    @DefaultValue("8") @Min(1) @Max(1024) int fallbackConcurrency,
    @DefaultValue("true") boolean evictInvalid) {
  public CacheProperties {
    policy(
        ttlMillis,
        nullTtlMillis,
        jitterMillis,
        loadWaitMillis,
        protectLoads,
        fallbackOnReadFailure);
  }

  public CachePolicy policy() {
    return policy(
        ttlMillis,
        nullTtlMillis,
        jitterMillis,
        loadWaitMillis,
        protectLoads,
        fallbackOnReadFailure);
  }

  private static CachePolicy policy(long t, long n, long j, long w, boolean p, boolean f) {
    return new CachePolicy(
        Duration.ofMillis(t),
        Duration.ofMillis(n),
        Duration.ofMillis(j),
        Duration.ofMillis(w),
        p,
        f);
  }
}
