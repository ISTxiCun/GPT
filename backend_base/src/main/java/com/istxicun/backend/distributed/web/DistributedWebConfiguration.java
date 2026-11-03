package com.istxicun.backend.distributed.web;

import com.istxicun.backend.distributed.DistributedProperties;
import com.istxicun.backend.distributed.ratelimit.RateLimit;
import com.istxicun.backend.distributed.ratelimit.RedisRateLimiter;
import com.istxicun.backend.distributed.submission.PreventDuplicateSubmission;
import com.istxicun.backend.distributed.submission.SubmissionGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration(proxyBeanMethods = false)
public class DistributedWebConfiguration implements WebMvcConfigurer {
  private final DistributedWebInterceptor interceptor;

  public DistributedWebConfiguration(
      ObjectProvider<RedisRateLimiter> limiter,
      ObjectProvider<SubmissionGuard> guard,
      ClientIpResolver ips) {
    interceptor = new DistributedWebInterceptor(limiter, guard, ips);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(interceptor).addPathPatterns("/api/**").order(10);
  }

  @Bean
  SmartInitializingSingleton distributedAnnotationValidator(
      @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings,
      DistributedProperties properties) {
    return () ->
        mappings
            .getHandlerMethods()
            .forEach(
                (mapping, method) -> {
                  RateLimit rate = method.getMethodAnnotation(RateLimit.class);
                  PreventDuplicateSubmission submission =
                      method.getMethodAnnotation(PreventDuplicateSubmission.class);
                  if (rate == null && submission == null) return;
                  Class<?> returnType = method.getMethod().getReturnType();
                  if (java.util.concurrent.Callable.class.isAssignableFrom(returnType)
                      || java.util.concurrent.CompletionStage.class.isAssignableFrom(returnType)
                      || org.springframework.web.context.request.async.DeferredResult.class
                          .isAssignableFrom(returnType)
                      || org.springframework.web.context.request.async.WebAsyncTask.class
                          .isAssignableFrom(returnType)
                      || org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
                          .class
                          .isAssignableFrom(returnType))
                    throw new IllegalStateException(
                        "V1 distributed annotations require synchronous MVC methods: " + method);
                  if (mapping.getPatternValues().isEmpty()
                      || mapping.getPatternValues().stream().anyMatch(p -> !p.startsWith("/api/")))
                    throw new IllegalStateException(
                        "Distributed annotations require /api/ controller paths: " + method);
                  if (rate != null) {
                    if (!properties.rateLimit().enabled())
                      throw new IllegalStateException(
                          "@RateLimit requires app.distributed.rate-limit.enabled=true: " + method);
                    RedisRateLimiter.validate(rate.key(), rate.limit(), rate.windowSeconds());
                  }
                  if (submission != null) {
                    if (!properties.submission().enabled())
                      throw new IllegalStateException(
                          "@PreventDuplicateSubmission requires"
                              + " app.distributed.submission.enabled=true: "
                              + method);
                    SubmissionGuard.validateWindow(submission.windowSeconds());
                    if (mapping.getMethodsCondition().getMethods().isEmpty()
                        || mapping.getMethodsCondition().getMethods().stream()
                            .anyMatch(
                                m ->
                                    !java.util.Set.of("POST", "PUT", "PATCH", "DELETE")
                                        .contains(m.name())))
                      throw new IllegalStateException(
                          "Submission guard requires an explicit write HTTP method: " + method);
                  }
                });
  }
}
