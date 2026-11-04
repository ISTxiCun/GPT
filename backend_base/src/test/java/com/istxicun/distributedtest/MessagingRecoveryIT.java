package com.istxicun.distributedtest;

import static org.assertj.core.api.Assertions.*;

import com.istxicun.backend.common.identity.ApplicationIdentity;
import com.istxicun.backend.distributed.messaging.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.*;

class MessagingRecoveryIT {
  @Test
  void connectionLossReturnsUnknownAndSameClientRecovers() throws Exception {
    var properties = MessagingTestSupport.properties(false);
    try (var proxy = new Proxy(properties.host(), properties.port())) {
      var cf = new CachingConnectionFactory("127.0.0.1", proxy.server.getLocalPort());
      cf.setUsername(properties.username());
      cf.setPassword(properties.password());
      cf.setConnectionTimeout(500);
      cf.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
      cf.setPublisherReturns(true);
      var template = new RabbitTemplate(cf);
      template.setMandatory(true);
      var names =
          new MessagingNames(new ApplicationIdentity("mq-recover-" + UUID.randomUUID(), "test"));
      var codec =
          new MessageCodec(
              MessagingTestSupport.mapper(),
              properties,
              new ApplicationIdentity("recovery", "test"));
      var publisher =
          new RabbitMessagePublisher(template, codec, names, properties, new SimpleMeterRegistry());
      var admin = new RabbitAdmin(cf);
      try {
        admin.declareExchange(new DirectExchange(names.events(), true, false));
        var message = codec.create("no.route", 1, Map.of());
        assertThat(publisher.publish(message).status()).isEqualTo(PublishResult.Status.RETURNED);
        proxy.disconnect();
        cf.resetConnection();
        assertThat(publisher.publish(message).status()).isEqualTo(PublishResult.Status.UNKNOWN);
        proxy.enabled.set(true);
        cf.resetConnection();
        assertThat(publisher.publish(message).status()).isEqualTo(PublishResult.Status.RETURNED);
      } finally {
        admin.deleteExchange(names.events());
        cf.destroy();
      }
    }
  }

  static class Proxy implements AutoCloseable {
    final ServerSocket server = new ServerSocket(0, 20, InetAddress.getLoopbackAddress());
    final ExecutorService workers = Executors.newCachedThreadPool();
    final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    final AtomicBoolean enabled = new AtomicBoolean(true);

    Proxy(String host, int port) throws Exception {
      workers.submit(
          () -> {
            while (!server.isClosed()) {
              try {
                Socket from = server.accept();
                if (!enabled.get()) {
                  from.close();
                  continue;
                }
                Socket to = new Socket(host, port);
                sockets.add(from);
                sockets.add(to);
                workers.submit(() -> copy(from, to));
                workers.submit(() -> copy(to, from));
              } catch (java.io.IOException ex) {
                if (!server.isClosed()) throw new RuntimeException(ex);
              }
            }
          });
    }

    void copy(Socket from, Socket to) {
      try {
        from.getInputStream().transferTo(to.getOutputStream());
      } catch (java.io.IOException ignored) {
      } finally {
        try {
          from.close();
          to.close();
        } catch (java.io.IOException ignored) {
        }
        sockets.remove(from);
        sockets.remove(to);
      }
    }

    void disconnect() {
      enabled.set(false);
      for (var socket : sockets)
        try {
          socket.close();
        } catch (java.io.IOException ignored) {
        }
    }

    public void close() throws Exception {
      disconnect();
      server.close();
      workers.shutdownNow();
    }
  }
}
