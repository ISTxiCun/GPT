package com.istxicun.backend.distributed.messaging;

import com.istxicun.backend.common.identity.ApplicationIdentity;

public final class MessagingNames {
  private final String prefix;

  public MessagingNames(ApplicationIdentity identity) {
    prefix = identity.namespace() + "." + identity.environment() + ".msg.";
  }

  public static String component(String value) {
    if (value == null || !value.matches("[a-z][a-z0-9.-]{0,63}"))
      throw new IllegalArgumentException("Invalid messaging name");
    return value;
  }

  /**
   * Reserved suffixes identify infrastructure queues, including across separately deployed groups.
   */
  public static String consumer(String value) {
    component(value);
    if (value.endsWith(".failed") || value.matches(".*\\.retry\\.[0-9]+"))
      throw new IllegalArgumentException(
          "Consumer name ends with reserved .failed or .retry.<number> suffix");
    return value;
  }

  public String events() {
    return prefix + "events";
  }

  public String workExchange() {
    return prefix + "work";
  }

  public String retryExchange() {
    return prefix + "retry";
  }

  public String failedExchange() {
    return prefix + "failed";
  }

  public String queue(String consumer) {
    return prefix + "consumer." + consumer(consumer);
  }

  public String retryQueue(String consumer, int level) {
    return queue(consumer) + ".retry." + level;
  }

  public String failedQueue(String consumer) {
    return queue(consumer) + ".failed";
  }
}
