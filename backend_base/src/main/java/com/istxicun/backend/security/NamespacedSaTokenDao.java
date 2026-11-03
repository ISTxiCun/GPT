package com.istxicun.backend.security;

import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Adapter for the supported Sa-Token version. All string/object/session/TTL/search operations share
 * this serializer.
 */
public final class NamespacedSaTokenDao extends SaTokenDaoForRedisTemplate {
  private final NamespaceKeySerializer keys;
  private final ApplicationIdentity identity;

  public NamespacedSaTokenDao(ApplicationIdentity identity, RedisConnectionFactory factory) {
    this.identity = identity;
    keys = new NamespaceKeySerializer(identity);
    init(factory);
  }

  @Override
  protected void initMore(RedisConnectionFactory factory) {
    stringRedisTemplate.setKeySerializer(keys);
    stringRedisTemplate.afterPropertiesSet();
  }

  void verifyIsolation(ApplicationIdentity expected) {
    if (!identity.equals(expected) || !isInit || stringRedisTemplate.getKeySerializer() != keys)
      throw new IllegalStateException("Sa-Token must use the shared identity key serializer");
  }
}
