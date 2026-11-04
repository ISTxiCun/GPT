package com.istxicun.demo;

import com.istxicun.backend.BackendApplication;
import org.springframework.boot.SpringApplication;

/** Build with distributed-demo; run using PropertiesLauncher and loader.main. */
public class MessagingDemoApplication {
  public static void main(String[] args) {
    var app = new SpringApplication(BackendApplication.class, MessagingDemoConfiguration.class);
    app.setAdditionalProfiles("messaging-demo");
    app.run(args);
  }
}
