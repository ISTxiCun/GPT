package com.istxicun.demo;

import com.istxicun.backend.BackendApplication;
import org.springframework.boot.SpringApplication;

/** Only compiled by -Pdistributed-demo. Never use this executable as a production app. */
public class DistributedDemoApplication {
  public static void main(String[] args) {
    var app = new SpringApplication(BackendApplication.class, DemoConfiguration.class);
    app.setAdditionalProfiles("distributed-demo");
    app.run(args);
  }
}
