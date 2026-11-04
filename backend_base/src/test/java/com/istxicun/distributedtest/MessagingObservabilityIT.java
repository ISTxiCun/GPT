package com.istxicun.distributedtest;

import static org.assertj.core.api.Assertions.*;

import cn.dev33.satoken.annotation.SaIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.istxicun.backend.BackendApplication;
import com.istxicun.backend.distributed.messaging.*;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.*;

@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@SpringBootTest(
    classes = {BackendApplication.class, MessagingObservabilityIT.Config.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.identity.environment=test",
      "management.server.port=0",
      "management.tracing.enabled=true",
      "management.tracing.sampling.probability=1.0",
      "management.otlp.tracing.export.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:mq-observation;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.data.redis.password=",
      "app.distributed.messaging.enabled=true",
      "app.distributed.messaging.consumers-enabled=true",
      "app.distributed.messaging.retry-delays-millis[0]=200",
      "app.distributed.messaging.retry-delays-millis[1]=400"
    })
class MessagingObservabilityIT {
  static final String NAMESPACE = "trace-it-" + UUID.randomUUID();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    var p = MessagingTestSupport.properties(true);
    registry.add("app.identity.namespace", () -> NAMESPACE);
    registry.add("app.distributed.messaging.host", p::host);
    registry.add("app.distributed.messaging.port", p::port);
    registry.add("app.distributed.messaging.username", p::username);
    registry.add("app.distributed.messaging.password", p::password);
  }

  @Autowired TestRestTemplate http;
  @Autowired Captures captures;
  @Autowired SdkTracerProvider provider;
  @Autowired Receiver receiver;
  @Autowired MessagingRuntime runtime;
  @Autowired MessagingNames names;
  @Autowired org.springframework.boot.actuate.health.HealthEndpointGroups groups;
  @Autowired org.springframework.amqp.rabbit.connection.CachingConnectionFactory connection;

  @Test
  void httpPublishConsumeRetryHaveBoundedSpansAndNoRequestMdcLeak() throws Exception {
    try {
      assertThat(groups.get("readiness").isMember("messaging")).isTrue();
      assertThat(groups.get("liveness").isMember("messaging")).isFalse();
      var first = http.postForObject("/api/public/observation-message", null, Map.class);
      assertThat(first.get("confirmed")).isEqualTo(true);
      assertThat(receiver.handled.await(10, TimeUnit.SECONDS)).isTrue();
      var second = http.postForObject("/api/public/observation-message", null, Map.class);
      assertThat(second.get("confirmed")).isEqualTo(true);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (receiver.seen.size() < 3 && System.nanoTime() < deadline) Thread.sleep(20);
      assertThat(receiver.seen).hasSize(3);
      provider.forceFlush().join(5, TimeUnit.SECONDS);
      assertThat(receiver.seen.get(0)).as("initial consume trace").isEqualTo(first.get("trace"));
      assertThat(receiver.seen.get(1)).as("retry consume trace").isEqualTo(first.get("trace"));
      assertThat(receiver.seen.get(2)).as("unrelated consume trace").isEqualTo(second.get("trace"));
      assertThat(first.get("trace")).isNotEqualTo(second.get("trace"));
      assertThat(receiver.requestIds).containsOnly("");
      // Listener observation ends after ACK; await the exported consumer spans as well.
      do {
        provider.forceFlush().join(5, TimeUnit.SECONDS);
        if (captures.spans.stream().filter(s -> s.getKind() == SpanKind.CONSUMER).count() >= 3)
          break;
        Thread.sleep(20);
      } while (System.nanoTime() < deadline);
      var spans =
          captures.spans.stream().filter(s -> s.getTraceId().equals(first.get("trace"))).toList();
      assertThat(spans).anyMatch(s -> s.getKind() == SpanKind.SERVER);
      assertThat(spans).anyMatch(s -> s.getKind() == SpanKind.PRODUCER);
      assertThat(spans.stream().filter(s -> s.getKind() == SpanKind.CONSUMER)).hasSize(2);
      assertThat(spans)
          .allSatisfy(
              s ->
                  assertThat(s.getEndEpochNanos() - s.getStartEpochNanos())
                      .isLessThan(TimeUnit.SECONDS.toNanos(10)));
      assertThat(spans.toString()).doesNotContain("SENSITIVE-MESSAGE-MARKER");
      receiver.holdNext.set(true);
      assertThat(
              http.postForObject("/api/public/observation-message", null, Map.class)
                  .get("confirmed"))
          .isEqualTo(true);
      assertThat(receiver.entered.await(5, TimeUnit.SECONDS)).isTrue();
      var stopping = Executors.newSingleThreadExecutor();
      try {
        var stopped =
            stopping.submit(
                () -> {
                  runtime.stop();
                });
        Thread.sleep(100);
        assertThat(stopped.isDone()).as("must wait for in-flight handler").isFalse();
        receiver.release.countDown();
        stopped.get(10, TimeUnit.SECONDS);
        var queue =
            new org.springframework.amqp.rabbit.core.RabbitAdmin(connection)
                .getQueueInfo(names.queue("observation"));
        assertThat(queue).isNotNull();
        assertThat(queue.getMessageCount()).as("completed delivery was ACKed").isZero();
      } finally {
        receiver.release.countDown();
        stopping.shutdownNow();
      }
    } finally {
      runtime.stop();
      var admin = new org.springframework.amqp.rabbit.core.RabbitAdmin(connection);
      admin.deleteQueue(names.queue("observation"));
      admin.deleteQueue(names.failedQueue("observation"));
      for (int i = 0; i < 2; i++) admin.deleteQueue(names.retryQueue("observation", i));
      for (String exchange :
          List.of(
              names.events(), names.workExchange(), names.retryExchange(), names.failedExchange()))
        admin.deleteExchange(exchange);
    }
  }

  static class Captures implements SpanExporter {
    final List<SpanData> spans = new CopyOnWriteArrayList<>();

    public CompletableResultCode export(Collection<SpanData> batch) {
      spans.addAll(batch);
      return CompletableResultCode.ofSuccess();
    }

    public CompletableResultCode flush() {
      return CompletableResultCode.ofSuccess();
    }

    public CompletableResultCode shutdown() {
      return flush();
    }
  }

  static class Receiver implements MessageHandler<Map<String, String>> {
    final Tracer tracer;
    final List<String> seen = new CopyOnWriteArrayList<>();
    final List<String> requestIds = new CopyOnWriteArrayList<>();
    final CountDownLatch handled = new CountDownLatch(1);
    final java.util.concurrent.atomic.AtomicBoolean holdNext =
        new java.util.concurrent.atomic.AtomicBoolean();
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    Receiver(Tracer tracer) {
      this.tracer = tracer;
    }

    public String consumerName() {
      return "observation";
    }

    public String eventType() {
      return "observation.changed";
    }

    public int schemaVersion() {
      return 1;
    }

    public TypeReference<Map<String, String>> payloadType() {
      return new TypeReference<>() {};
    }

    public void handle(MessageEnvelope envelope, Map<String, String> payload) {
      seen.add(tracer.currentSpan().context().traceId());
      requestIds.add(Objects.toString(MDC.get("requestId"), ""));
      if (seen.size() == 1) throw new IllegalStateException("retry once");
      if (holdNext.getAndSet(false)) {
        entered.countDown();
        try {
          if (!release.await(10, TimeUnit.SECONDS))
            throw new IllegalStateException("test handler timed out");
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(ex);
        }
      }
      handled.countDown();
    }
  }

  @RestController
  static class Endpoint {
    final MessagePublisher publisher;
    final MessageCodec codec;
    final Tracer tracer;

    Endpoint(MessagePublisher publisher, MessageCodec codec, Tracer tracer) {
      this.publisher = publisher;
      this.codec = codec;
      this.tracer = tracer;
    }

    @SaIgnore
    @PostMapping("/api/public/observation-message")
    Map<String, Object> publish() {
      var result =
          publisher.publish(
              codec.create("observation.changed", 1, Map.of("value", "SENSITIVE-MESSAGE-MARKER")));
      return Map.of(
          "trace", tracer.currentSpan().context().traceId(), "confirmed", result.confirmed());
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  @Import(Endpoint.class)
  static class Config {
    @Bean
    Captures captures() {
      return new Captures();
    }

    @Bean
    Receiver receiver(Tracer tracer) {
      return new Receiver(tracer);
    }
  }
}
