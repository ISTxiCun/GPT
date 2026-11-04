package com.istxicun.backend.distributed.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.util.Objects;

public final class CacheType<T> {
  private final JavaType type;

  private CacheType(java.lang.reflect.Type type) {
    this.type = TypeFactory.defaultInstance().constructType(Objects.requireNonNull(type));
  }

  public static <T> CacheType<T> of(Class<T> type) {
    return new CacheType<>(type);
  }

  public static <T> CacheType<T> of(TypeReference<T> type) {
    return new CacheType<>(type.getType());
  }

  JavaType javaType() {
    return type;
  }
}
