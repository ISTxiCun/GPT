package com.istxicun.backend.security;

import com.istxicun.backend.common.identity.ApplicationIdentity;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/** Namespaces physical session keys while preserving logical Sa-Token IDs and header names. */
public final class NamespaceKeySerializer implements RedisSerializer<String> {
  private final String prefix;

  public NamespaceKeySerializer(ApplicationIdentity identity) {
    prefix = identity.prefix("session");
  }

  @Override
  public byte[] serialize(String key) {
    return key == null ? null : StringRedisSerializer.UTF_8.serialize(prefix + key);
  }

  @Override
  public String deserialize(byte[] bytes) {
    if (bytes == null) return null;
    String key = StringRedisSerializer.UTF_8.deserialize(bytes);
    if (key == null || !key.startsWith(prefix))
      throw new SerializationException("Session key belongs to another namespace/environment");
    return key.substring(prefix.length());
  }
}
