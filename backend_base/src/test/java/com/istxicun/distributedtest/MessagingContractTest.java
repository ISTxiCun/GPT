package com.istxicun.distributedtest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.distributed.messaging.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MessagingContractTest {
  final MessagingProperties p = MessagingTestSupport.properties(false);
  final ApplicationIdentity identity = new ApplicationIdentity("test", "a");
  final MessageCodec codec = new MessageCodec(MessagingTestSupport.mapper(), p, identity);

  @Test
  void disabledCreatesNoBrokerObjectsOrWorkers() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
        .withUserConfiguration(MessagingConfiguration.class, OutboxConfiguration.class)
        .run(
            ctx ->
                assertThat(ctx)
                    .hasNotFailed()
                    .doesNotHaveBean(ConnectionFactory.class)
                    .doesNotHaveBean(MessagePublisher.class)
                    .doesNotHaveBean(MessagingRuntime.class)
                    .doesNotHaveBean(OutboxDispatcher.class));
  }

  @Test
  void malformedConfigurationFailsEvenBeforeConnection() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
        .withUserConfiguration(MessagingConfiguration.class)
        .withPropertyValues("app.distributed.messaging.outbox.enabled=true")
        .run(
            ctx ->
                assertThat(ctx)
                    .hasFailed()
                    .getFailure()
                    .hasStackTraceContaining("Outbox requires"));
  }

  @Test
  void boundedExplicitGenericPayloadAndIdentity() {
    var envelope = codec.create("inventory.changed", 1, List.of(Map.of("count", 7)));
    var decoded = codec.decode(codec.encode(envelope));
    assertThat(decoded).isEqualTo(envelope);
    MessageHandler<List<Map<String, Integer>>> handler =
        new MessageHandler<>() {
          public String consumerName() {
            return "search";
          }

          public String eventType() {
            return "inventory.changed";
          }

          public int schemaVersion() {
            return 1;
          }

          public TypeReference<List<Map<String, Integer>>> payloadType() {
            return new TypeReference<>() {};
          }

          public void handle(MessageEnvelope e, List<Map<String, Integer>> v) {}
        };
    assertThat(codec.payload(decoded, handler).get(0).get("count")).isEqualTo(7);
    assertThatThrownBy(() -> codec.encode(codec.create("inventory.changed", 1, "x".repeat(5000))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> codec.decode("{}".getBytes()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new MessagingNames(identity).events())
        .isNotEqualTo(new MessagingNames(new ApplicationIdentity("test", "b")).events());
  }

  @Test
  void publishingInsideDatabaseTransactionAndOutsideScopeIsRejected() {
    RabbitTemplate template = mock(RabbitTemplate.class);
    var publisher =
        new RabbitMessagePublisher(
            template, codec, new MessagingNames(identity), p, new SimpleMeterRegistry());
    org.springframework.transaction.support.TransactionSynchronizationManager
        .setActualTransactionActive(true);
    try {
      assertThatThrownBy(() -> publisher.publish(codec.create("inventory.changed", 1, Map.of())))
          .isInstanceOf(IllegalStateException.class);
    } finally {
      org.springframework.transaction.support.TransactionSynchronizationManager
          .setActualTransactionActive(false);
    }
    assertThatThrownBy(
            () ->
                publisher.send(
                    "other.environment.events",
                    "inventory.changed",
                    new Message(new byte[0], new MessageProperties())))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(template);
  }

  @Test
  void mandatoryReturnsOverrideAckAndTimeoutIsUnknown() {
    RabbitTemplate template = mock(RabbitTemplate.class);
    var publisher =
        new RabbitMessagePublisher(
            template, codec, new MessagingNames(identity), p, new SimpleMeterRegistry());
    doAnswer(
            i -> {
              CorrelationData data = i.getArgument(3);
              data.setReturned(
                  new ReturnedMessage(
                      i.getArgument(2), 312, "NO_ROUTE", i.getArgument(0), i.getArgument(1)));
              data.getFuture().complete(new CorrelationData.Confirm(true, null));
              return null;
            })
        .when(template)
        .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    assertThat(publisher.publish(codec.create("missing", 1, "x")).status())
        .isEqualTo(PublishResult.Status.RETURNED);
    doAnswer(i -> null)
        .when(template)
        .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    assertThat(publisher.publish(codec.create("missing", 1, "x")).status())
        .isEqualTo(PublishResult.Status.UNKNOWN);
  }
}
