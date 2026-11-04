package com.istxicun.backend.distributed;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.distributed", ignoreUnknownFields = false)
@Validated
public record DistributedProperties(
    @Valid @DefaultValue Lock lock,
    @Valid @DefaultValue com.istxicun.backend.distributed.cache.CacheProperties cache,
    @Valid @DefaultValue Feature rateLimit,
    @Valid @DefaultValue Feature submission,
    List<String> trustedProxies) {
  public DistributedProperties {
    trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
  }

  public record Lock(
      @DefaultValue("false") boolean enabled,
      @DefaultValue("30000") @Min(1000) @Max(300000) long watchdogMillis) {}

  public record Feature(@DefaultValue("false") boolean enabled) {}
}
