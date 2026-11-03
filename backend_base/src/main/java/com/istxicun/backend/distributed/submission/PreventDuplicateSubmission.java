package com.istxicun.backend.distributed.submission;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PreventDuplicateSubmission {
  long windowSeconds() default 5;
}
