package com.labtrace.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

  private Login login = new Login();
  private Reset reset = new Reset();
  private Checkin checkin = new Checkin();

  public Login getLogin() {
    return login;
  }

  public Reset getReset() {
    return reset;
  }

  public Checkin getCheckin() {
    return checkin;
  }

  public static class Login {
    private int capacity = 10;
    private Duration window = Duration.ofMinutes(15);

    public int getCapacity() {
      return capacity;
    }

    public void setCapacity(int capacity) {
      this.capacity = capacity;
    }

    public Duration getWindow() {
      return window;
    }

    public void setWindow(Duration window) {
      this.window = window;
    }
  }

  public static class Reset {
    private int capacity = 5;
    private Duration window = Duration.ofMinutes(15);

    public int getCapacity() {
      return capacity;
    }

    public void setCapacity(int capacity) {
      this.capacity = capacity;
    }

    public Duration getWindow() {
      return window;
    }

    public void setWindow(Duration window) {
      this.window = window;
    }
  }

  public static class Checkin {
    private int capacity = 10;
    private Duration window = Duration.ofMinutes(1);

    public int getCapacity() {
      return capacity;
    }

    public void setCapacity(int capacity) {
      this.capacity = capacity;
    }

    public Duration getWindow() {
      return window;
    }

    public void setWindow(Duration window) {
      this.window = window;
    }
  }
}
