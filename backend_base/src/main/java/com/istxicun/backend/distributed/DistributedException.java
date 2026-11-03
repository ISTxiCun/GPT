package com.istxicun.backend.distributed;

import com.istxicun.backend.common.exception.ApplicationException;
import com.istxicun.backend.common.exception.FailureCategory;

public class DistributedException extends ApplicationException {
  public DistributedException(FailureCategory category, String code, String message) {
    this(category, code, message, null, null);
  }

  public DistributedException(
      FailureCategory category,
      String code,
      String message,
      Long retryAfterSeconds,
      Throwable cause) {
    super(category, code, message, retryAfterSeconds, cause);
  }

  public static DistributedException unavailable(Throwable cause) {
    return new DistributedException(
        FailureCategory.UNAVAILABLE,
        "DISTRIBUTED_UNAVAILABLE",
        "基础服务暂时不可用，请确认操作结果后再重试",
        null,
        cause);
  }
}
