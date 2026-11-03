package com.istxicun.regression;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.istxicun.backend.BackendApplication;
import com.istxicun.backend.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

@SpringBootTest(
    classes = BackendApplication.class,
    properties = {
      "app.identity.environment=cors-test",
      "spring.datasource.url=jdbc:h2:mem:cors;MODE=MySQL",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.data.redis.password=",
      "management.server.port=0",
      "app.cors.allowed-origins=https://frontend.example",
      "app.cors.allowed-headers=Content-Range,X-Upload-Id",
      "app.cors.exposed-headers=X-Upload-Offset"
    })
@AutoConfigureMockMvc
@Import(CorsWebIntegrationTest.Probe.class)
class CorsWebIntegrationTest {
  @RestController
  static class Probe {
    @PostMapping("/api/public/cors-probe")
    ApiResponse<String> probe(HttpServletResponse response) {
      response.setHeader("X-Upload-Offset", "128");
      return ApiResponse.ok("ok");
    }
  }

  @Autowired MockMvc mvc;

  @Test
  void extendedPreflightAndResponseHeadersWorkAlongsideRequiredHeaders() throws Exception {
    mvc.perform(
            options("/api/public/cors-probe")
                .header("Origin", "https://frontend.example")
                .header("Access-Control-Request-Method", "POST")
                .header(
                    "Access-Control-Request-Headers",
                    "Authorization, Idempotency-Key, Content-Range, X-Upload-Id"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example"));
    mvc.perform(post("/api/public/cors-probe").header("Origin", "https://frontend.example"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Upload-Offset", "128"))
        .andExpect(
            header()
                .string(
                    "Access-Control-Expose-Headers",
                    org.hamcrest.Matchers.containsString("X-Upload-Offset")))
        .andExpect(
            header()
                .string(
                    "Access-Control-Expose-Headers",
                    org.hamcrest.Matchers.containsString("Retry-After")))
        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
  }

  @Test
  void unapprovedHeaderAndOriginAreStillRejected() throws Exception {
    mvc.perform(
            options("/api/public/cors-probe")
                .header("Origin", "https://frontend.example")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-Unapproved"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/public/cors-probe").header("Origin", "https://untrusted.example"))
        .andExpect(status().isForbidden());
  }
}
