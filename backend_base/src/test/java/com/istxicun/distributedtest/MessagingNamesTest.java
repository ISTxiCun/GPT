package com.istxicun.distributedtest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.distributed.messaging.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

class MessagingNamesTest {
  @Test
  void reservedConsumersFailBeforeAnyBrokerConnection() {
    var cf = mock(ConnectionFactory.class);
    for (var name :
        List.of(
            "orders.failed", "orders.retry.0", "orders.retry.7", "a.failed.failed", "a.retry.01")) {
      MessageHandler<String> handler =
          new MessageHandler<>() {
            public String consumerName() {
              return name;
            }

            public String eventType() {
              return "orders.changed";
            }

            public int schemaVersion() {
              return 1;
            }

            public TypeReference<String> payloadType() {
              return new TypeReference<>() {};
            }

            public void handle(MessageEnvelope e, String payload) {}
          };
      assertThatThrownBy(
              () ->
                  new MessagingRuntime(
                      cf,
                      null,
                      null,
                      null,
                      MessagingTestSupport.properties(true),
                      null,
                      List.of(handler)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("reserved");
    }
    verifyNoInteractions(cf);
  }

  @Test
  void allowedDottedConsumersHaveDisjointWorkFailureAndRetryQueues() {
    var names = new MessagingNames(new ApplicationIdentity("test", "env"));
    Set<String> physical = new HashSet<>();
    for (var consumer :
        List.of(
            "orders",
            "orders.projection",
            "orders.retry.v1",
            "orders.retry.0.extra",
            "orders-failed")) {
      assertThat(physical.add(names.queue(consumer))).isTrue();
      assertThat(physical.add(names.failedQueue(consumer))).isTrue();
      for (int i = 0; i < 8; i++) assertThat(physical.add(names.retryQueue(consumer, i))).isTrue();
    }
    assertThat(MessagingNames.component("orders.failed"))
        .isEqualTo("orders.failed"); // Event types still allow these names.
    assertThatThrownBy(() -> names.queue("orders.failed"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> names.queue("orders.retry.0"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
