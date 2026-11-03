package com.istxicun.regression;

import static org.assertj.core.api.Assertions.*;

import com.istxicun.backend.config.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CorsConfigurationTest {
  final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ConfigurationPropertiesAutoConfiguration.class,
                  ValidationAutoConfiguration.class))
          .withUserConfiguration(WebConfig.class);

  @Test
  void configurationExtendsTheBaselineCaseInsensitively() {
    runner
        .withPropertyValues(
            "app.cors.allowed-headers=Content-Range,authorization,X-Upload-Id",
            "app.cors.exposed-headers=X-Upload-Offset,retry-after",
            "app.cors.max-age-seconds=120")
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              var props = ctx.getBean(CorsProperties.class);
              assertThat(props.effectiveAllowedHeaders())
                  .containsExactly(
                      "Authorization",
                      "Content-Type",
                      "X-Request-Id",
                      "Idempotency-Key",
                      "Content-Range",
                      "X-Upload-Id");
              assertThat(props.effectiveExposedHeaders())
                  .containsExactly("X-Request-Id", "Retry-After", "X-Upload-Offset");
              assertThat(props.maxAgeSeconds()).isEqualTo(120);
            });
  }

  @Test
  void emptyExtensionsPreserveFrameworkHeaders() {
    runner
        .withPropertyValues("app.cors.allowed-headers=", "app.cors.exposed-headers=")
        .run(
            ctx ->
                assertThat(ctx.getBean(CorsProperties.class).effectiveAllowedHeaders())
                    .contains("Authorization", "Idempotency-Key"));
  }

  @Test
  void invalidHeaderWildcardAndNegativeTtlAreRejected() {
    for (String property :
        List.of(
            "app.cors.allowed-headers=*",
            "app.cors.exposed-headers=bad header",
            "app.cors.max-age-seconds=-1",
            "app.cors.unrecognized=true"))
      runner.withPropertyValues(property).run(ctx -> assertThat(ctx).hasFailed());
  }
}
