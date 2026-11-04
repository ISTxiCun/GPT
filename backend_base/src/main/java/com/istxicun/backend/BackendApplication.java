package com.istxicun.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    excludeName = {
      "cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate",
      "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
      "org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpTracingAutoConfiguration",
      "org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration"
    })
public class BackendApplication {
  public static void main(String[] args) {
    SpringApplication.run(BackendApplication.class, args);
  }
}
