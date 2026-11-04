package com.istxicun.backend.distributed.messaging;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.distributed.messaging", ignoreUnknownFields = false)
@Validated
public record MessagingProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("localhost") @NotBlank String host,
    @DefaultValue("5672") @Min(1) @Max(65535) int port,
    @DefaultValue("/") @NotBlank String virtualHost,
    @DefaultValue("") String username,
    @DefaultValue("") String password,
    @DefaultValue("false") boolean tls,
    @DefaultValue("5000") @Min(100) @Max(30000) int confirmTimeoutMillis,
    @DefaultValue("1048576") @Min(1024) @Max(16777216) int maxMessageBytes,
    @DefaultValue("16") @Min(1) @Max(1024) int prefetch,
    @DefaultValue("1") @Min(1) @Max(16) int concurrency,
    @DefaultValue({"1000", "10000", "60000"}) List<@Min(100) @Max(3600000) Long> retryDelaysMillis,
    @DefaultValue("false") boolean consumersEnabled,
    @Valid @DefaultValue Outbox outbox) {
  public MessagingProperties {
    retryDelaysMillis = List.copyOf(retryDelaysMillis);
    if (retryDelaysMillis.size() > 8
        || !retryDelaysMillis.equals(retryDelaysMillis.stream().sorted().distinct().toList()))
      throw new IllegalArgumentException(
          "Retry delays must be increasing, unique and at most eight");
    if (enabled
        && (username == null || username.isBlank() || password == null || password.isBlank()))
      throw new IllegalArgumentException("Explicit messaging credentials required");
    if (outbox != null && outbox.enabled() && !enabled)
      throw new IllegalArgumentException("Outbox requires messaging.enabled");
  }

  public record Outbox(
      @DefaultValue("false") boolean enabled,
      @DefaultValue("1000") @Min(100) @Max(60000) int pollMillis,
      @DefaultValue("10") @Min(1) @Max(100) int batchSize,
      @DefaultValue("60000") @Min(1000) @Max(600000) int leaseMillis,
      @DefaultValue("10") @Min(1) @Max(100) int maxAttempts) {}
}
