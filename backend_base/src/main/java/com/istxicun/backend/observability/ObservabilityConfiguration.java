package com.istxicun.backend.observability;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.core.instrument.config.MeterFilter;
import org.slf4j.MDC;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

@Configuration(proxyBeanMethods = false)
public class ObservabilityConfiguration {
  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      prefix = "management.tracing",
      name = "enabled",
      havingValue = "true")
  org.springframework.boot.autoconfigure.data.redis.ClientResourcesBuilderCustomizer redisTracing(
      io.micrometer.observation.ObservationRegistry observations) {
    return builder ->
        builder.tracing(
            new io.lettuce.core.tracing.MicrometerTracing(observations, "redis", false));
  }

  @Bean
  MeterRegistryCustomizer<io.micrometer.core.instrument.MeterRegistry> cardinalityBudget() {
    return registry ->
        registry
            .config()
            .meterFilter(
                MeterFilter.maximumAllowableTags(
                    "http.server.requests", "uri", 100, MeterFilter.deny()));
  }

  /** Applies to Boot's managed executor; arbitrary threads are deliberately not covered. */
  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(TaskDecorator.class)
  TaskDecorator observationTaskDecorator() {
    return task -> {
      var snapshot = ContextSnapshot.captureAll();
      var submittingMdc = MDC.getCopyOfContextMap();
      return () -> {
        var previous = MDC.getCopyOfContextMap();
        try (var ignored = snapshot.setThreadLocals()) {
          if (submittingMdc == null) MDC.clear();
          else MDC.setContextMap(submittingMdc);
          task.run();
        } finally {
          if (previous == null) MDC.clear();
          else MDC.setContextMap(previous);
        }
      };
    };
  }
}
