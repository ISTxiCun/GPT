package com.istxicun.backend.distributed.messaging;

import com.istxicun.backend.common.identity.ApplicationIdentity;
import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "app.distributed.messaging.outbox",
    name = "enabled",
    havingValue = "true")
public class OutboxConfiguration {
  @Bean
  OutboxWriter outboxWriter(DataSource source, MessageCodec codec, ApplicationIdentity identity) {
    return new OutboxWriter(source, codec, identity);
  }

  @Bean
  @org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
  OutboxDispatcher outboxDispatcher(
      DataSource source,
      ApplicationIdentity identity,
      MessagingProperties p,
      MessageCodec codec,
      MessagePublisher publisher,
      MeterRegistry metrics) {
    return new OutboxDispatcher(source, identity, p, codec, publisher, metrics);
  }
}
