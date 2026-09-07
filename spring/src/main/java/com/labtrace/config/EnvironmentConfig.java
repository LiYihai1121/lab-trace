package com.labtrace.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentConfig {

  private final Environment env;

  public EnvironmentConfig(Environment env) {
    this.env = env;
  }

  public String name() {
    return env.getProperty("NODE_ENV", "development");
  }

  public String label() {
    String n = name();
    return switch (n) {
      case "test" -> "测试环境";
      case "production" -> "生产环境";
      default -> "开发环境";
    };
  }
}
