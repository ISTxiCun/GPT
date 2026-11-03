package com.istxicun.backend.distributed;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.distributed")
@Validated
public record DistributedProperties(
    @DefaultValue("backend-base") String namespace,
    @DefaultValue("") String environment,
    @Valid @DefaultValue Lock lock,
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

  @AssertTrue(
      message =
          "Enabling distributed features requires namespace and environment matching"
              + " [A-Za-z0-9_-]{1,64}; set APP_ENV explicitly")
  public boolean isNamespaceValid() {
    return !(lock.enabled() || rateLimit.enabled() || submission.enabled())
        || (namespace != null
            && namespace.matches("[A-Za-z0-9_-]{1,64}")
            && environment != null
            && environment.matches("[A-Za-z0-9_-]{1,64}"));
  }
}
