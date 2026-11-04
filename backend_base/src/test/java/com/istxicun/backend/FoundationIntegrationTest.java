package com.istxicun.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.dev33.satoken.annotation.SaIgnore;
import com.istxicun.backend.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

@SpringBootTest(
    properties = {
      "app.identity.environment=test",
      "spring.datasource.url=jdbc:h2:mem:foundation;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.data.redis.password=",
      "management.server.port=0",
      "app.cors.allowed-origins=http://localhost:5173"
    })
@AutoConfigureMockMvc
@Import(FoundationIntegrationTest.ProbeController.class)
class FoundationIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired org.springframework.context.ApplicationContext context;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  @Test
  void disabledObservabilityCreatesNoRemoteExporters() {
    org.assertj.core.api.Assertions.assertThat(
            context.getBeansOfType(io.opentelemetry.sdk.trace.export.BatchSpanProcessor.class))
        .isEmpty();
    org.assertj.core.api.Assertions.assertThat(
            context.getBeansOfType(io.opentelemetry.sdk.trace.export.SpanExporter.class))
        .isEmpty();
    org.assertj.core.api.Assertions.assertThat(
            context.getBeansOfType(io.micrometer.prometheusmetrics.PrometheusMeterRegistry.class))
        .isEmpty();
  }

  @Test
  void disabledMessagingCreatesNoClientsWorkersOrOptionalTables() {
    org.assertj.core.api.Assertions.assertThat(
            context.getBeansOfType(
                org.springframework.amqp.rabbit.connection.ConnectionFactory.class))
        .isEmpty();
    org.assertj.core.api.Assertions.assertThat(
            context.getBeansOfType(
                com.istxicun.backend.distributed.messaging.MessagePublisher.class))
        .isEmpty();
    org.assertj.core.api.Assertions.assertThat(
            context.getBeansOfType(
                com.istxicun.backend.distributed.messaging.OutboxDispatcher.class))
        .isEmpty();
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) IN"
                    + " ('INFRA_MESSAGE_OUTBOX','INFRA_MESSAGE_CONSUMED')",
                Integer.class))
        .isZero();
  }

  @RestController
  static class ProbeController {
    record Input(@NotBlank String name) {}

    @GetMapping("/api/probe")
    ApiResponse<String> protectedEndpoint() {
      return ApiResponse.ok("secret");
    }

    @SaIgnore
    @GetMapping("/api/ignored")
    ApiResponse<String> ignored() {
      return ApiResponse.ok("public");
    }

    @PostMapping("/api/public/probe")
    ApiResponse<String> publicEndpoint(@Valid @RequestBody Input input) {
      return ApiResponse.ok(input.name());
    }

    @GetMapping("/api/public/crash")
    void crash() {
      throw new IllegalStateException("sensitive-detail");
    }
  }

  @Test
  void protectedRouteRequiresLogin() throws Exception {
    mvc.perform(get("/api/probe"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void explicitlyIgnoredRouteIsPublic() throws Exception {
    mvc.perform(get("/api/ignored")).andExpect(status().isOk());
  }

  @Test
  void validationAndSuccessContracts() throws Exception {
    mvc.perform(
            post("/api/public/probe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    mvc.perform(
            post("/api/public/probe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"hello\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value("hello"));
  }

  @Test
  void corsPreflightDoesNotRequireLogin() throws Exception {
    mvc.perform(
            options("/api/probe")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization, Idempotency-Key"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    mvc.perform(
            options("/api/probe")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
  }

  @Test
  void errorsDoNotLeakInternalDetails() throws Exception {
    mvc.perform(get("/api/public/crash"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("服务暂时不可用"));
    mvc.perform(get("/not-found")).andExpect(status().isNotFound());
  }

  @Test
  void demoEndpointsNotRegisteredInDefaultApplication() throws Exception {
    mvc.perform(post("/api/public/demo/login")).andExpect(status().isNotFound());
  }

  @Test
  void apiDocumentationDisabledByDefault() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
  }
}
