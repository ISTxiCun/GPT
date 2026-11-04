package com.istxicun.backend.distributed.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public record CachePolicy(
    Duration ttl,
    Duration nullTtl,
    Duration jitter,
    Duration loadWait,
    boolean protectLoads,
    boolean fallbackOnReadFailure) {
  public CachePolicy {
    bounded(ttl, 1, 86400000, "ttl");
    bounded(nullTtl, 1, 300000, "nullTtl");
    bounded(jitter, 0, 86400000, "jitter");
    bounded(loadWait, 0, 30000, "loadWait");
    if (jitter.toMillis() >= ttl.toMillis() || ttl.toMillis() + jitter.toMillis() > 86400000)
      throw new IllegalArgumentException("ttl +/- jitter must be in 1ms..1 day");
  }

  private static void bounded(Duration d, long min, long max, String name) {
    if (d == null
        || d.compareTo(Duration.ofMillis(min)) < 0
        || d.compareTo(Duration.ofMillis(max)) > 0)
      throw new IllegalArgumentException(name + " outside allowed range");
  }

  Duration valueTtl() {
    long j = jitter.toMillis();
    return Duration.ofMillis(
        ttl.toMillis() + (j == 0 ? 0 : ThreadLocalRandom.current().nextLong(-j, j + 1)));
  }
}
