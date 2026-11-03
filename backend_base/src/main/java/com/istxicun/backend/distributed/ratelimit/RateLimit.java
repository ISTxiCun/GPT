package com.istxicun.backend.distributed.ratelimit;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
  String key();

  Dimension dimension() default Dimension.USER;

  long limit();

  long windowSeconds();

  FailurePolicy failurePolicy() default FailurePolicy.CLOSED;

  enum Dimension {
    USER,
    IP,
    GLOBAL
  }

  enum FailurePolicy {
    CLOSED,
    OPEN
  }
}
