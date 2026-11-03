package com.istxicun.regression;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.dao.SaTokenDao;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.config.identity.ApplicationIdentityConfiguration;
import com.istxicun.backend.distributed.DistributedConfiguration;
import com.istxicun.backend.distributed.key.RedisKeyFactory;
import com.istxicun.backend.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.StringRedisSerializer;

class IdentityAndSessionConfigurationTest {
  final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ConfigurationPropertiesAutoConfiguration.class,
                  ValidationAutoConfiguration.class))
          .withUserConfiguration(
              ApplicationIdentityConfiguration.class, SaTokenStorageConfiguration.class)
          .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
          .withPropertyValues("app.identity.namespace=project", "app.identity.environment=test");

  @Test
  void validIdentityProducesOneMandatoryNamespacedDao() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasNotFailed().hasSingleBean(SaTokenDao.class);
          assertThat(ctx.getBean(SaTokenDao.class)).isExactlyInstanceOf(NamespacedSaTokenDao.class);
          var serializer =
              (NamespaceKeySerializer)
                  ctx.getBean(NamespacedSaTokenDao.class).stringRedisTemplate.getKeySerializer();
          assertThat(
                  new String(
                      serializer.serialize("Authorization:login:token:value"),
                      java.nio.charset.StandardCharsets.UTF_8))
              .isEqualTo("project:test:session:Authorization:login:token:value");
        });
  }

  @Test
  void missingOrInvalidEnvironmentFailsEvenWithoutDistributedFeatures() {
    for (String value : new String[] {"", "prod:*", "../prod"})
      runner
          .withPropertyValues("app.identity.environment=" + value)
          .run(ctx -> assertThat(ctx).hasFailed().getFailure().hasStackTraceContaining("APP_ENV"));
  }

  @Test
  void secondUnscopedDaoCannotSilentlyReplaceSessionStorage() {
    runner
        .withBean("unsafeDao", SaTokenDao.class, () -> mock(SaTokenDao.class))
        .run(
            ctx ->
                assertThat(ctx)
                    .hasFailed()
                    .getFailure()
                    .hasStackTraceContaining("Exactly one namespaced"));
  }

  @Test
  void replacingThePhysicalKeySerializerFailsAtStartup() {
    runner
        .withBean(
            "unsafeCustomizer",
            BeanPostProcessor.class,
            () ->
                new BeanPostProcessor() {
                  @Override
                  public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof NamespacedSaTokenDao dao)
                      dao.stringRedisTemplate.setKeySerializer(StringRedisSerializer.UTF_8);
                    return bean;
                  }
                })
        .run(
            ctx ->
                assertThat(ctx)
                    .hasFailed()
                    .getFailure()
                    .hasStackTraceContaining("shared identity key serializer"));
  }

  @Test
  void replacingDaoWithAnotherIdentityFailsAtStartup() {
    runner
        .withBean(
            "wrongIdentityCustomizer",
            BeanPostProcessor.class,
            () ->
                new BeanPostProcessor() {
                  @Override
                  public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof NamespacedSaTokenDao)
                      return new NamespacedSaTokenDao(
                          new ApplicationIdentity("wrong", "test"),
                          mock(RedisConnectionFactory.class));
                    return bean;
                  }
                })
        .run(
            ctx ->
                assertThat(ctx)
                    .hasFailed()
                    .getFailure()
                    .hasStackTraceContaining("shared identity key serializer"));
  }

  @Test
  void physicalKeysAreScopedAndForeignKeysCannotDeserialize() {
    var first = new NamespaceKeySerializer(new ApplicationIdentity("project", "test"));
    var otherEnv = new NamespaceKeySerializer(new ApplicationIdentity("project", "prod"));
    var otherProject = new NamespaceKeySerializer(new ApplicationIdentity("other", "test"));
    byte[] key = first.serialize("Authorization:login:session:42");
    assertThat(first.deserialize(key)).isEqualTo("Authorization:login:session:42");
    assertThatThrownBy(() -> otherEnv.deserialize(key)).hasMessageContaining("another namespace");
    assertThatThrownBy(() -> otherProject.deserialize(key))
        .hasMessageContaining("another namespace");
    var identity = new ApplicationIdentity("project", "test");
    assertThat(new RedisKeyFactory(identity).key("lock", "resource"))
        .startsWith("project:test:dist:");
  }

  @Test
  void legacyDistributedIdentityOverridesAreRejectedInsteadOfBeingIgnored() {
    runner
        .withUserConfiguration(DistributedConfiguration.class)
        .withPropertyValues("app.distributed.environment=other")
        .run(ctx -> assertThat(ctx).hasFailed());
  }
}
