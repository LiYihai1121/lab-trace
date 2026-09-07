package com.labtrace.controller;

import com.labtrace.config.EnvironmentConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  private final EnvironmentConfig environmentConfig;

  public HealthController(EnvironmentConfig environmentConfig) {
    this.environmentConfig = environmentConfig;
  }

  @GetMapping("/api/health")
  public HealthResponse health() {
    return new HealthResponse("ok", "lab-trace", environmentConfig.name(), environmentConfig.label(), "1.1.0");
  }

  public record HealthResponse(String status, String service, String environment, String environmentLabel,
                               String version) {
  }
}
