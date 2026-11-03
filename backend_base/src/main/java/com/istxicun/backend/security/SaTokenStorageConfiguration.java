package com.istxicun.backend.security;

import cn.dev33.satoken.dao.SaTokenDao;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import java.util.Map;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration(proxyBeanMethods = false)
public class SaTokenStorageConfiguration {
  @Bean
  NamespacedSaTokenDao saTokenDao(ApplicationIdentity identity, RedisConnectionFactory factory) {
    return new NamespacedSaTokenDao(identity, factory);
  }

  @Bean
  SmartInitializingSingleton sessionIsolationGuard(
      ApplicationContext context, NamespacedSaTokenDao expected, ApplicationIdentity identity) {
    return () -> {
      Map<String, SaTokenDao> daos = context.getBeansOfType(SaTokenDao.class);
      if (daos.size() != 1 || daos.values().iterator().next() != expected)
        throw new IllegalStateException(
            "Exactly one namespaced SaTokenDao is required; unscoped/custom DAO overrides are"
                + " rejected");
      expected.verifyIsolation(identity);
    };
  }
}
