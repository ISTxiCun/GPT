package com.istxicun.backend.web.error;

import com.istxicun.backend.common.exception.FailureCategory;
import org.springframework.http.HttpStatus;

public final class HttpFailureMapper {
  private HttpFailureMapper() {}

  public static HttpStatus status(FailureCategory category) {
    return switch (category) {
      case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
      case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
      case FORBIDDEN -> HttpStatus.FORBIDDEN;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case CONFLICT -> HttpStatus.CONFLICT;
      case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
      case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
      case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }
}
