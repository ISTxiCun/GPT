package com.istxicun.backend.config.identity;

import com.istxicun.backend.common.identity.ApplicationIdentity;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApplicationIdentityProperties.class)
public class ApplicationIdentityConfiguration {
  @Bean
  ApplicationIdentity applicationIdentity(ApplicationIdentityProperties properties) {
    return new ApplicationIdentity(properties.namespace(), properties.environment());
  }
}
