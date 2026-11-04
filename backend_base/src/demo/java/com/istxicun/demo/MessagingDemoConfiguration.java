package com.istxicun.demo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.distributed.messaging.*;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@Profile("messaging-demo")
public class MessagingDemoConfiguration {
  @Bean
  MessageHandler<Map<String, String>> demoMessageHandler(
      DataSource source, ApplicationIdentity identity) {
    var idempotency = new JdbcConsumerIdempotency(source, identity);
    return new MessageHandler<>() {
      public String consumerName() {
        return "demo-notices";
      }

      public String eventType() {
        return "demo.notice";
      }

      public int schemaVersion() {
        return 1;
      }

      public TypeReference<Map<String, String>> payloadType() {
        return new TypeReference<>() {};
      }

      public void handle(MessageEnvelope envelope, Map<String, String> payload) {
        idempotency.execute(
            consumerName(),
            envelope,
            () ->
                org.slf4j.LoggerFactory.getLogger(MessagingDemoConfiguration.class)
                    .info("Demo notice processed: {}", envelope.messageId()));
      }
    };
  }

  @Bean
  ApplicationRunner demoOutboxNotice(DataSource source, OutboxWriter writer, MessageCodec codec) {
    return args ->
        new TransactionTemplate(new DataSourceTransactionManager(source))
            .executeWithoutResult(
                status ->
                    writer.append(codec.create("demo.notice", 1, Map.of("message", "hello"))));
  }
}
