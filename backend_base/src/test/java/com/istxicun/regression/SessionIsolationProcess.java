package com.istxicun.regression;

import cn.dev33.satoken.stp.StpUtil;
import com.istxicun.backend.BackendApplication;
import com.istxicun.backend.common.api.ApiResponse;
import java.nio.file.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;

public class SessionIsolationProcess {
  @TestConfiguration(proxyBeanMethods = false)
  public static class Configuration {
    @Bean
    Probe probe() {
      return new Probe();
    }
  }

  @RestController
  public static class Probe {
    @PostMapping("/api/public/isolation/login")
    ApiResponse<String> login() {
      StpUtil.login("shared-user");
      return ApiResponse.ok(StpUtil.getTokenValue());
    }

    @GetMapping("/api/isolation/me")
    ApiResponse<String> me() {
      return ApiResponse.ok(StpUtil.getLoginIdAsString());
    }

    @PostMapping("/api/isolation/logout")
    ApiResponse<Void> logout() {
      StpUtil.logout();
      return ApiResponse.ok(null);
    }

    @PostMapping("/api/isolation/kickout")
    ApiResponse<Void> kickout() {
      StpUtil.kickout("shared-user");
      return ApiResponse.ok(null);
    }

    @GetMapping("/api/isolation/tokens")
    ApiResponse<java.util.List<String>> tokens() {
      return ApiResponse.ok(StpUtil.searchTokenValue("", 0, -1, true));
    }
  }

  public static void main(String[] args) throws Exception {
    var context =
        (ServletWebServerApplicationContext)
            new SpringApplication(BackendApplication.class, Configuration.class).run(args);
    Files.writeString(
        Path.of(System.getenv("ISOLATION_PORT_FILE")),
        Integer.toString(context.getWebServer().getPort()));
  }
}
