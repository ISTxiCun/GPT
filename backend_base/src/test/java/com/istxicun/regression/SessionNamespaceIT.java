package com.istxicun.regression;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class SessionNamespaceIT {
  @TempDir Path temp;
  final List<Process> processes = new ArrayList<>();
  final String namespace = "session-it-" + UUID.randomUUID();
  final HttpClient http = HttpClient.newHttpClient();
  final ObjectMapper json = new ObjectMapper();
  String host = System.getenv().getOrDefault("TEST_REDIS_HOST", "127.0.0.1");
  int redisPort = Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_PORT", "6379"));
  String password = System.getenv().getOrDefault("TEST_REDIS_PASSWORD", "");
  int database = Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_DATABASE", "0"));

  int start(String project, String environment, String id) throws Exception {
    Path portFile = temp.resolve(id + ".port"), log = temp.resolve(id + ".log");
    var command =
        new ArrayList<>(
            List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx192m",
                "-cp",
                System.getProperty(
                    "surefire.test.class.path", System.getProperty("java.class.path")),
                SessionIsolationProcess.class.getName(),
                "--server.port=0",
                "--management.server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:" + id + ";MODE=MySQL",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--app.identity.namespace=" + project,
                "--app.identity.environment=" + environment,
                "--sa-token.timeout=60",
                "--spring.main.banner-mode=off"));
    var builder =
        new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
    builder
        .environment()
        .putAll(
            Map.of(
                "ISOLATION_PORT_FILE",
                portFile.toString(),
                "REDIS_HOST",
                host,
                "REDIS_PORT",
                Integer.toString(redisPort),
                "REDIS_PASSWORD",
                password,
                "REDIS_DATABASE",
                Integer.toString(database)));
    Process process = builder.start();
    processes.add(process);
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
    while (System.nanoTime() < deadline) {
      if (Files.exists(portFile)) return Integer.parseInt(Files.readString(portFile));
      if (!process.isAlive())
        throw new AssertionError("Isolation process " + id + " failed; see " + log);
      Thread.sleep(100);
    }
    throw new AssertionError("Isolation process " + id + " timed out; see " + log);
  }

  HttpResponse<String> request(int port, String method, String path, String token)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(8))
            .method(method, HttpRequest.BodyPublishers.noBody());
    if (token != null) builder.header("Authorization", "Bearer " + token);
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  String login(int port) throws Exception {
    var result = request(port, "POST", "/api/public/isolation/login", null);
    assertThat(result.statusCode()).isEqualTo(200);
    return json.readTree(result.body()).path("data").asText();
  }

  @Test
  void sessionsAreSharedOnlyWithinOneIdentityAndAllSessionOperationsStayScoped() throws Exception {
    int a = start(namespace, "test", "a"),
        peer = start(namespace, "test", "peer"),
        prod = start(namespace, "prod", "prod"),
        other = start(namespace + "-other", "test", "other");
    String token = login(a);
    assertThat(request(peer, "GET", "/api/isolation/me", token).statusCode()).isEqualTo(200);
    assertThat(request(prod, "GET", "/api/isolation/me", token).statusCode()).isEqualTo(401);
    assertThat(request(other, "GET", "/api/isolation/me", token).statusCode()).isEqualTo(401);
    String prodToken = login(prod), otherToken = login(other);
    var tokens =
        json.readTree(request(a, "GET", "/api/isolation/tokens", token).body()).path("data");
    assertThat(tokens.toString()).contains(token).doesNotContain(prodToken, otherToken);
    assertThat(request(peer, "POST", "/api/isolation/kickout", token).statusCode()).isEqualTo(200);
    assertThat(request(a, "GET", "/api/isolation/me", token).statusCode()).isEqualTo(401);
    assertThat(request(prod, "GET", "/api/isolation/me", prodToken).statusCode()).isEqualTo(200);
    assertThat(request(other, "GET", "/api/isolation/me", otherToken).statusCode()).isEqualTo(200);
    String second = login(a);
    assertThat(request(peer, "POST", "/api/isolation/logout", second).statusCode()).isEqualTo(200);
    assertThat(request(a, "GET", "/api/isolation/me", second).statusCode()).isEqualTo(401);
    assertThat(request(prod, "GET", "/api/isolation/me", prodToken).statusCode()).isEqualTo(200);
    assertThat(request(other, "GET", "/api/isolation/me", otherToken).statusCode()).isEqualTo(200);
  }

  @AfterEach
  void cleanup() throws Exception {
    for (Process process : processes) process.destroy();
    for (Process process : processes)
      if (!process.waitFor(8, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        process.waitFor(3, TimeUnit.SECONDS);
      }
    var config = new RedisStandaloneConfiguration(host, redisPort);
    config.setDatabase(database);
    if (!password.isEmpty()) config.setPassword(password);
    var factory = new LettuceConnectionFactory(config);
    factory.afterPropertiesSet();
    factory.start();
    try {
      var redis = new StringRedisTemplate(factory);
      for (String pattern : List.of(namespace + ":*", namespace + "-other:*")) {
        var keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
      }
    } finally {
      factory.destroy();
    }
  }
}
