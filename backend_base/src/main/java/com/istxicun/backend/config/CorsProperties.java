package com.istxicun.backend.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.cors", ignoreUnknownFields = false)
@Validated
public record CorsProperties(
    List<String> allowedOrigins,
    List<String> allowedHeaders,
    List<String> exposedHeaders,
    @DefaultValue("3600") @Min(0) @Max(86400) long maxAgeSeconds) {
  private static final List<String> REQUIRED_ALLOWED =
      List.of("Authorization", "Content-Type", "X-Request-Id", "Idempotency-Key");
  private static final List<String> REQUIRED_EXPOSED = List.of("X-Request-Id", "Retry-After");

  public CorsProperties {
    allowedOrigins = clean(allowedOrigins);
    allowedHeaders = clean(allowedHeaders);
    exposedHeaders = clean(exposedHeaders);
  }

  private static List<String> clean(List<String> values) {
    return values == null
        ? List.of()
        : values.stream().map(String::trim).filter(v -> !v.isEmpty()).toList();
  }

  @AssertTrue(
      message =
          "CORS headers must be explicit HTTP field names, at most 64 entries; wildcard and control"
              + " characters are not allowed")
  public boolean isHeadersValid() {
    return valid(allowedHeaders) && valid(exposedHeaders);
  }

  private static boolean valid(List<String> values) {
    return values.size() <= 64
        && values.stream()
            .allMatch(
                v ->
                    v.length() <= 128
                        && !v.equals("*")
                        && v.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+"));
  }

  private static List<String> merge(List<String> required, List<String> configured) {
    var unique = new LinkedHashMap<String, String>();
    for (String value : required) unique.put(value.toLowerCase(Locale.ROOT), value);
    for (String value : configured) unique.putIfAbsent(value.toLowerCase(Locale.ROOT), value);
    return List.copyOf(unique.values());
  }

  public List<String> effectiveAllowedHeaders() {
    return merge(REQUIRED_ALLOWED, allowedHeaders);
  }

  public List<String> effectiveExposedHeaders() {
    return merge(REQUIRED_EXPOSED, exposedHeaders);
  }
}
