package com.istxicun.backend.observability;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.distributed.key.RedisKeyFactory;
import com.istxicun.backend.distributed.ratelimit.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class ObservabilityTest {
  @Test
  void openAndClosedBackendFailureHaveDistinctMetricsWithoutSubjectTags() {
    var redis = mock(StringRedisTemplate.class);
    when(redis.execute(
            any(org.springframework.data.redis.core.script.RedisScript.class),
            anyList(),
            any(Object[].class)))
        .thenThrow(new DataAccessResourceFailureException("offline"));
    var registry = new SimpleMeterRegistry();
    var limiter =
        new RedisRateLimiter(
            redis, new RedisKeyFactory(new ApplicationIdentity("test", "local")), registry);
    for (int i = 0; i < 100; i++)
      limiter.check(
          "dynamic-" + i,
          RateLimit.Dimension.IP,
          "subject-" + i,
          10,
          60,
          RateLimit.FailurePolicy.OPEN);
    assertThat(registry.get("distributed.rate.limit").tag("result", "open").counter().count())
        .isEqualTo(100);
    assertThatThrownBy(
            () ->
                limiter.check(
                    "closed",
                    RateLimit.Dimension.IP,
                    "secret",
                    10,
                    60,
                    RateLimit.FailurePolicy.CLOSED))
        .isInstanceOf(com.istxicun.backend.distributed.DistributedException.class);
    assertThat(registry.getMeters()).hasSize(2);
  }

  @Test
  void reusedWorkerRestoresContextEvenWhenTaskFails() throws Exception {
    var decorator = new ObservabilityConfiguration().observationTaskDecorator();
    var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    try {
      MDC.put("requestId", "first");
      var first =
          decorator.decorate(
              () -> {
                assertThat(MDC.get("requestId")).isEqualTo("first");
                throw new IllegalStateException("expected");
              });
      MDC.clear();
      assertThatThrownBy(() -> worker.submit(first).get())
          .hasCauseInstanceOf(IllegalStateException.class);
      worker.submit(decorator.decorate(() -> assertThat(MDC.get("requestId")).isNull())).get();
      worker.submit(() -> assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty()).get();
    } finally {
      MDC.clear();
      worker.shutdownNow();
    }
  }
}
