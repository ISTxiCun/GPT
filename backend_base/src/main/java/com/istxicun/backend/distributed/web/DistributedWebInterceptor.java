package com.istxicun.backend.distributed.web;

import cn.dev33.satoken.stp.StpUtil;
import com.istxicun.backend.distributed.DistributedException;
import com.istxicun.backend.distributed.ratelimit.RateLimit;
import com.istxicun.backend.distributed.ratelimit.RedisRateLimiter;
import com.istxicun.backend.distributed.submission.PreventDuplicateSubmission;
import com.istxicun.backend.distributed.submission.SubmissionGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

public final class DistributedWebInterceptor implements HandlerInterceptor {
  private final ObjectProvider<RedisRateLimiter> limiter;
  private final ObjectProvider<SubmissionGuard> guard;
  private final ClientIpResolver ips;

  public DistributedWebInterceptor(
      ObjectProvider<RedisRateLimiter> limiter,
      ObjectProvider<SubmissionGuard> guard,
      ClientIpResolver ips) {
    this.limiter = limiter;
    this.guard = guard;
    this.ips = ips;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod method)) return true;
    RateLimit rate = method.getMethodAnnotation(RateLimit.class);
    if (rate != null) {
      String subject =
          switch (rate.dimension()) {
            case USER -> StpUtil.getLoginIdAsString();
            case IP -> ips.resolve(request);
            case GLOBAL -> "global";
          };
      limiter
          .getObject()
          .check(
              rate.key(),
              rate.dimension(),
              subject,
              rate.limit(),
              rate.windowSeconds(),
              rate.failurePolicy());
    }
    PreventDuplicateSubmission submission =
        method.getMethodAnnotation(PreventDuplicateSubmission.class);
    if (submission != null) {
      String user = StpUtil.getLoginIdAsString();
      var headers = Collections.list(request.getHeaders("Idempotency-Key"));
      if (headers.size() != 1)
        throw new DistributedException(
            HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "需要唯一的 Idempotency-Key 请求头");
      String endpoint =
          request.getMethod()
              + ":"
              + request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      guard.getObject().acquire(user, endpoint, headers.get(0), submission.windowSeconds());
    }
    return true;
  }
}
