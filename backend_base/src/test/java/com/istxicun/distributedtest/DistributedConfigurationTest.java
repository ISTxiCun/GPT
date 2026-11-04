package com.istxicun.distributedtest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.istxicun.backend.config.identity.ApplicationIdentityConfiguration;
import com.istxicun.backend.distributed.*;
import com.istxicun.backend.distributed.key.RedisKeyFactory;
import com.istxicun.backend.distributed.lock.LockExecutor;
import com.istxicun.backend.distributed.ratelimit.*;
import com.istxicun.backend.distributed.submission.*;
import com.istxicun.backend.distributed.web.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class DistributedConfigurationTest {
  final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ConfigurationPropertiesAutoConfiguration.class,
                  ValidationAutoConfiguration.class))
          .withUserConfiguration(
              DistributedConfiguration.class,
              com.istxicun.backend.distributed.cache.CacheConfiguration.class,
              ApplicationIdentityConfiguration.class)
          .withPropertyValues("app.identity.environment=test");

  public static class Endpoints {
    @RateLimit(key = "test", limit = 1, windowSeconds = 1)
    public void rate() {}

    @PreventDuplicateSubmission
    public void submit() {}

    @PreventDuplicateSubmission
    public java.util.concurrent.Callable<String> async() {
      return () -> "result";
    }

    @RateLimit(key = "test", limit = 0, windowSeconds = 1)
    public void invalid() {}
  }

  RequestMappingHandlerMapping mappings(String method, String path, RequestMethod verb)
      throws Exception {
    var mapping = mock(RequestMappingHandlerMapping.class);
    when(mapping.getHandlerMethods())
        .thenReturn(
            Map.of(
                RequestMappingInfo.paths(path).methods(verb).build(),
                new HandlerMethod(new Endpoints(), Endpoints.class.getMethod(method))));
    return mapping;
  }

  @Test
  void disabledByDefaultWithoutRedisConnection() {
    runner.run(
        ctx -> {
          assertThat(ctx)
              .hasNotFailed()
              .doesNotHaveBean(RedissonClient.class)
              .doesNotHaveBean(LockExecutor.class)
              .doesNotHaveBean(RedisRateLimiter.class)
              .doesNotHaveBean(SubmissionGuard.class);
        });
  }

  @Test
  void cacheValidatesPoliciesAndLockDependency() {
    runner
        .withPropertyValues("app.distributed.cache.ttl-millis=0")
        .run(ctx -> assertThat(ctx).hasFailed());
    runner
        .withPropertyValues(
            "app.distributed.cache.enabled=true", "app.distributed.cache.protect-loads=true")
        .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
        .withBean(
            com.fasterxml.jackson.databind.ObjectMapper.class,
            com.fasterxml.jackson.databind.ObjectMapper::new)
        .withBean(
            io.micrometer.core.instrument.MeterRegistry.class,
            io.micrometer.core.instrument.simple.SimpleMeterRegistry::new)
        .run(
            ctx ->
                assertThat(ctx)
                    .hasFailed()
                    .getFailure()
                    .hasStackTraceContaining("requires app.distributed.lock.enabled"));
  }

  @Test
  void explicitEnvironmentRequiredEvenWithFeaturesDisabled() {
    runner
        .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
        .withPropertyValues("app.identity.environment=")
        .run(ctx -> assertThat(ctx).hasFailed().getFailure().hasStackTraceContaining("APP_ENV"));
  }

  @Test
  void disabledAnnotationsFailAtStartup() throws Exception {
    runner
        .withUserConfiguration(DistributedWebConfiguration.class)
        .withBean(
            "requestMappingHandlerMapping",
            RequestMappingHandlerMapping.class,
            () -> {
              try {
                return mappings("rate", "/api/test", RequestMethod.GET);
              } catch (Exception ex) {
                throw new RuntimeException(ex);
              }
            })
        .run(
            ctx ->
                assertThat(ctx)
                    .hasFailed()
                    .getFailure()
                    .hasStackTraceContaining("@RateLimit requires"));
  }

  @Test
  void submissionRequiresExplicitWriteMethod() throws Exception {
    runner
        .withUserConfiguration(DistributedWebConfiguration.class)
        .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
        .withPropertyValues(
            "app.distributed.submission.enabled=true", "app.identity.environment=test")
        .withBean(
            "requestMappingHandlerMapping",
            RequestMappingHandlerMapping.class,
            () -> {
              try {
                return mappings("submit", "/api/test", RequestMethod.GET);
              } catch (Exception ex) {
                throw new RuntimeException(ex);
              }
            })
        .run(
            ctx ->
                assertThat(ctx)
                    .hasFailed()
                    .getFailure()
                    .hasStackTraceContaining("explicit write HTTP method"));
  }

  @Test
  void annotationsOutsideProtectedPathFailAtStartup() throws Exception {
    runner
        .withUserConfiguration(DistributedWebConfiguration.class)
        .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
        .withPropertyValues(
            "app.distributed.rate-limit.enabled=true", "app.identity.environment=test")
        .withBean(
            "requestMappingHandlerMapping",
            RequestMappingHandlerMapping.class,
            () -> {
              try {
                return mappings("rate", "/outside", RequestMethod.GET);
              } catch (Exception ex) {
                throw new RuntimeException(ex);
              }
            })
        .run(
            ctx ->
                assertThat(ctx)
                    .hasFailed()
                    .getFailure()
                    .hasStackTraceContaining("/api/ controller paths"));
  }

  @Test
  void invalidAnnotationParametersFailAtStartup() throws Exception {
    runner
        .withUserConfiguration(DistributedWebConfiguration.class)
        .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
        .withPropertyValues(
            "app.distributed.rate-limit.enabled=true", "app.identity.environment=test")
        .withBean(
            "requestMappingHandlerMapping",
            RequestMappingHandlerMapping.class,
            () -> {
              try {
                return mappings("invalid", "/api/test", RequestMethod.GET);
              } catch (Exception ex) {
                throw new RuntimeException(ex);
              }
            })
        .run(
            ctx ->
                assertThat(ctx)
                    .hasFailed()
                    .getFailure()
                    .hasStackTraceContaining("limit 1..1000000"));
  }

  @Test
  void asyncAnnotatedMethodsFailAtStartup() {
    runner
        .withUserConfiguration(DistributedWebConfiguration.class)
        .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
        .withPropertyValues(
            "app.distributed.submission.enabled=true", "app.identity.environment=test")
        .withBean(
            "requestMappingHandlerMapping",
            RequestMappingHandlerMapping.class,
            () -> {
              try {
                return mappings("async", "/api/test", RequestMethod.POST);
              } catch (Exception ex) {
                throw new RuntimeException(ex);
              }
            })
        .run(
            ctx ->
                assertThat(ctx)
                    .hasFailed()
                    .getFailure()
                    .hasStackTraceContaining("synchronous MVC methods"));
  }

  @Test
  void keyComponentsCannotCollideAndEnvironmentIsolated() {
    runner.run(
        ctx -> {
          var keys = ctx.getBean(RedisKeyFactory.class);
          assertThat(keys.key("lock", "a:b", "c")).isNotEqualTo(keys.key("lock", "a", "b:c"));
          assertThat(keys.key("lock", "private-value")).doesNotContain("private-value");
        });
  }

  @Test
  void forwardedHeadersIgnoredUnlessImmediatePeerTrusted() {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("192.0.2.20");
    request.addHeader("X-Forwarded-For", "198.51.100.10");
    assertThat(new ClientIpResolver(List.of()).resolve(request)).isEqualTo("192.0.2.20");
    request.setRemoteAddr("127.0.0.1");
    request.removeHeader("X-Forwarded-For");
    request.addHeader("X-Forwarded-For", "203.0.113.1, 198.51.100.2, 10.0.0.2");
    assertThat(new ClientIpResolver(List.of("127.0.0.1", "10.0.0.2")).resolve(request))
        .isEqualTo("198.51.100.2");
    assertThatThrownBy(() -> new ClientIpResolver(List.of("localhost")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void activeTransactionRejectedBeforeLockAcquisition() {
    runner.run(
        ctx -> {
          var client = mock(RedissonClient.class);
          var locks = new LockExecutor(client, ctx.getBean(RedisKeyFactory.class));
          TransactionSynchronizationManager.setActualTransactionActive(true);
          try {
            assertThatThrownBy(() -> locks.execute("test", Duration.ZERO, () -> "never"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside a transaction");
            verifyNoInteractions(client);
          } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
          }
        });
  }
}
