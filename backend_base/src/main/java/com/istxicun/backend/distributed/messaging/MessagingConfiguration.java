package com.istxicun.backend.distributed.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingConfiguration {
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(
      prefix = "app.distributed.messaging",
      name = "enabled",
      havingValue = "true")
  static class Enabled {
    @Bean(destroyMethod = "destroy")
    CachingConnectionFactory messagingConnectionFactory(MessagingProperties properties)
        throws Exception {
      var nativeFactory = new com.rabbitmq.client.ConnectionFactory();
      nativeFactory.setHost(properties.host());
      nativeFactory.setPort(properties.port());
      nativeFactory.setUsername(properties.username());
      nativeFactory.setPassword(properties.password());
      nativeFactory.setVirtualHost(properties.virtualHost());
      nativeFactory.setConnectionTimeout(5000);
      nativeFactory.setRequestedHeartbeat(30);
      nativeFactory.setAutomaticRecoveryEnabled(false);
      if (properties.tls()) {
        nativeFactory.useSslProtocol(javax.net.ssl.SSLContext.getDefault());
        nativeFactory.enableHostnameVerification();
      }
      var factory = new CachingConnectionFactory(nativeFactory);
      factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
      factory.setPublisherReturns(true);
      return factory;
    }

    @Bean
    RabbitTemplate messagingRabbitTemplate(CachingConnectionFactory messagingConnectionFactory) {
      var template = new RabbitTemplate(messagingConnectionFactory);
      template.setMandatory(true);
      return template;
    }

    @Bean
    MessagingNames messagingNames(ApplicationIdentity identity) {
      return new MessagingNames(identity);
    }

    @Bean
    MessageCodec messageCodec(
        ObjectMapper mapper, MessagingProperties p, ApplicationIdentity identity) {
      return new MessageCodec(mapper, p, identity);
    }

    @Bean
    RabbitMessagePublisher messagePublisher(
        RabbitTemplate template,
        MessageCodec codec,
        MessagingNames names,
        MessagingProperties p,
        MeterRegistry metrics) {
      return new RabbitMessagePublisher(template, codec, names, p, metrics);
    }

    @Bean
    MessagingRuntime messagingRuntime(
        CachingConnectionFactory connection,
        RabbitMessagePublisher publisher,
        MessageCodec codec,
        MessagingNames names,
        MessagingProperties p,
        MeterRegistry metrics,
        ObjectProvider<MessageHandler<?>> handlers) {
      return new MessagingRuntime(
          connection, publisher, codec, names, p, metrics, handlers.orderedStream().toList());
    }
  }
}
