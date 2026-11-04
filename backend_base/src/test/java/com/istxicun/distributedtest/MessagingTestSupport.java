package com.istxicun.distributedtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.istxicun.backend.distributed.messaging.*;
import java.util.List;

final class MessagingTestSupport {
  static ObjectMapper mapper() {
    return new ObjectMapper().findAndRegisterModules();
  }

  static MessagingProperties properties(boolean consume) {
    return new MessagingProperties(
        true,
        System.getenv().getOrDefault("TEST_RABBIT_HOST", "127.0.0.1"),
        Integer.parseInt(System.getenv().getOrDefault("TEST_RABBIT_PORT", "5672")),
        "/",
        System.getenv().getOrDefault("TEST_RABBIT_USER", "messaging"),
        System.getenv().getOrDefault("TEST_RABBIT_PASSWORD", "messaging-test"),
        false,
        1000,
        4096,
        2,
        1,
        List.of(200L, 400L),
        consume,
        new MessagingProperties.Outbox(false, 1000, 1, 4000, 3));
  }
}
