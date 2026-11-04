package com.istxicun.backend.distributed.cache;

/** Include tenant/user scope in businessId when caching permission-dependent data. */
public record CacheKey(String region, String version, String businessId) {
  public CacheKey {
    if (region == null
        || !region.matches("[a-zA-Z0-9_-]{1,64}")
        || version == null
        || !version.matches("[a-zA-Z0-9_-]{1,64}")
        || businessId == null
        || businessId.isBlank()
        || businessId.length() > 4096) throw new IllegalArgumentException("Invalid cache key");
  }
}
