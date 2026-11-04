package com.istxicun.backend.observability;

import static org.assertj.core.api.Assertions.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.*;
import org.springframework.boot.actuate.autoconfigure.tracing.otlp.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ObservabilitySwitchTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  MetricsAutoConfiguration.class,
                  ObservationAutoConfiguration.class,
                  org.springframework.boot.actuate.autoconfigure.opentelemetry
                      .OpenTelemetryAutoConfiguration.class,
                  NoopTracerAutoConfiguration.class,
                  MicrometerTracingAutoConfiguration.class,
                  PrometheusMetricsExportAutoConfiguration.class))
          .withUserConfiguration(ObservabilityConfiguration.class)
          .withPropertyValues(
              "management.tracing.enabled=false",
              "management.otlp.tracing.export.enabled=false",
              "management.otlp.tracing.endpoint=http://127.0.0.1:1/v1/traces",
              "management.prometheus.metrics.export.enabled=false");

  @Test
  void defaultOffCreatesNoSdkBatchSendingWorker() {
    runner.run(
        context -> {
          assertThat(context)
              .hasNotFailed()
              .doesNotHaveBean(io.opentelemetry.sdk.trace.export.BatchSpanProcessor.class);
          assertThat(context).doesNotHaveBean(SpanExporter.class);
        });
  }

  @Test
  void prometheusCanRunWithTracingDisabled() {
    runner
        .withPropertyValues("management.prometheus.metrics.export.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(PrometheusMeterRegistry.class);
              assertThat(context).doesNotHaveBean(SpanExporter.class);
            });
  }

  @Test
  void localTracingDoesNotCreateRemoteExporter() {
    runner
        .withPropertyValues("management.tracing.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(io.micrometer.tracing.Tracer.class);
              assertThat(context)
                  .doesNotHaveBean(SpanExporter.class)
                  .doesNotHaveBean(PrometheusMeterRegistry.class);
            });
  }

  @Test
  void tracingDisabledMustNotCreateExporterEvenIfExportIsRequested() {
    runner
        .withPropertyValues("management.otlp.tracing.export.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed().doesNotHaveBean(SpanExporter.class);
            });
  }

  @Test
  void tracingAndExportBothEnabledCreateRemoteExporter() {
    runner
        .withPropertyValues(
            "management.tracing.enabled=true", "management.otlp.tracing.export.enabled=true")
        .run(context -> assertThat(context).hasNotFailed().hasSingleBean(SpanExporter.class));
  }

  @Test
  void saturatedExporterDropsSpansWithoutBlockingCaller() {
    var exporter = new BlockedExporter();
    runner
        .withPropertyValues(
            "management.tracing.enabled=true",
            "management.tracing.sampling.probability=1.0",
            "management.tracing.opentelemetry.export.max-queue-size=32",
            "management.tracing.opentelemetry.export.max-batch-size=8",
            "management.tracing.opentelemetry.export.timeout=10s")
        .withBean(SpanExporter.class, () -> exporter)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var tracer =
                  context
                      .getBean(io.opentelemetry.api.OpenTelemetry.class)
                      .getTracer("saturation-test");
              for (int i = 0; i < 8; i++) tracer.spanBuilder("initial").startSpan().end();
              assertThat(exporter.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
              try {
                long start = System.nanoTime();
                for (int i = 0; i < 10000; i++) tracer.spanBuilder("pressure").startSpan().end();
                assertThat(java.time.Duration.ofNanos(System.nanoTime() - start))
                    .isLessThan(java.time.Duration.ofSeconds(5));
              } finally {
                exporter.blocked.succeed();
              }
              assertThat(
                      context
                          .getBean(io.opentelemetry.sdk.trace.SdkTracerProvider.class)
                          .forceFlush()
                          .join(10, java.util.concurrent.TimeUnit.SECONDS)
                          .isSuccess())
                  .isTrue();
              assertThat(exporter.received.get()).isBetween(8, 40);
            });
  }

  static class BlockedExporter implements SpanExporter {
    final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
    final java.util.concurrent.atomic.AtomicInteger received =
        new java.util.concurrent.atomic.AtomicInteger();
    final io.opentelemetry.sdk.common.CompletableResultCode blocked =
        new io.opentelemetry.sdk.common.CompletableResultCode();

    public io.opentelemetry.sdk.common.CompletableResultCode export(
        java.util.Collection<io.opentelemetry.sdk.trace.data.SpanData> spans) {
      received.addAndGet(spans.size());
      entered.countDown();
      return blocked;
    }

    public io.opentelemetry.sdk.common.CompletableResultCode flush() {
      return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess();
    }

    public io.opentelemetry.sdk.common.CompletableResultCode shutdown() {
      blocked.succeed();
      return flush();
    }
  }

  @Test
  void routeBudgetDeniesAdditionalRoutesButAcceptsExistingOnes() {
    var registry = new SimpleMeterRegistry();
    try {
      new ObservabilityConfiguration().cardinalityBudget().customize(registry);
      for (int i = 0; i < 120; i++)
        registry
            .timer("http.server.requests", "uri", "/route-" + i)
            .record(java.time.Duration.ofMillis(1));
      registry
          .timer("http.server.requests", "uri", "/route-0")
          .record(java.time.Duration.ofMillis(1));
      assertThat(registry.getMeters()).hasSize(100);
      assertThat(registry.get("http.server.requests").tag("uri", "/route-0").timer().count())
          .isEqualTo(2);
    } finally {
      registry.close();
    }
  }
}
