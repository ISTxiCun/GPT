package com.istxicun.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.distributed.messaging.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Local operator tool: requires OS access, explicit authorization flag and a durable audit file.
 */
public class MessagingReplayCommand {
  public static void main(String[] args) throws Exception {
    if (args.length != 1)
      throw new IllegalArgumentException("Usage: MessagingReplayCommand <consumer>");
    if (!"yes".equals(System.getenv("MQ_REPLAY_AUTHORIZED")))
      throw new SecurityException(
          "MQ_REPLAY_AUTHORIZED=yes required from authorized operator environment");
    var identity = new ApplicationIdentity(required("APP_NAMESPACE"), required("APP_ENV"));
    String username = required("RABBITMQ_USERNAME"), password = required("RABBITMQ_PASSWORD");
    var p =
        new MessagingProperties(
            true,
            System.getenv().getOrDefault("RABBITMQ_HOST", "localhost"),
            Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672")),
            System.getenv().getOrDefault("RABBITMQ_VHOST", "/"),
            username,
            password,
            Boolean.parseBoolean(System.getenv().getOrDefault("RABBITMQ_TLS", "false")),
            5000,
            1048576,
            1,
            1,
            List.of(),
            false,
            new MessagingProperties.Outbox(false, 1000, 1, 60000, 10));
    var nativeFactory = new com.rabbitmq.client.ConnectionFactory();
    nativeFactory.setHost(p.host());
    nativeFactory.setPort(p.port());
    nativeFactory.setVirtualHost(p.virtualHost());
    nativeFactory.setUsername(username);
    nativeFactory.setPassword(password);
    nativeFactory.setConnectionTimeout(5000);
    nativeFactory.setAutomaticRecoveryEnabled(false);
    if (p.tls()) {
      nativeFactory.useSslProtocol(javax.net.ssl.SSLContext.getDefault());
      nativeFactory.enableHostnameVerification();
    }
    var cf = new CachingConnectionFactory(nativeFactory);
    cf.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
    cf.setPublisherReturns(true);
    var template = new RabbitTemplate(cf);
    template.setMandatory(true);
    var names = new MessagingNames(identity);
    var publisher =
        new RabbitMessagePublisher(
            template,
            new MessageCodec(new ObjectMapper().findAndRegisterModules(), p, identity),
            names,
            p,
            new SimpleMeterRegistry());
    var path = Path.of(required("MQ_REPLAY_AUDIT_FILE"));
    // The OS lock also rate-limits concurrent CLI processes sharing this audit file.
    try (var file =
            FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
        var lock = file.lock()) {
      new FailedMessageReplayer(template, publisher, names)
          .replayOne(
              args[0],
              System.getProperty("user.name"),
              () -> {
                if (!"yes".equals(System.getenv("MQ_REPLAY_AUTHORIZED")))
                  throw new SecurityException("Replay denied");
              },
              (operator, event) -> {
                try {
                  var buffer =
                      StandardCharsets.UTF_8.encode(
                          Instant.now() + " " + operator + " " + event + System.lineSeparator());
                  while (buffer.hasRemaining()) file.write(buffer);
                  file.force(true);
                } catch (java.io.IOException ex) {
                  throw new java.io.UncheckedIOException(ex);
                }
              });
      Thread.sleep(1000);
    } finally {
      template.destroy();
      cf.destroy();
    }
  }

  private static String required(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " required");
    return value;
  }
}
