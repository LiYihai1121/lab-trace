package com.labtrace.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RateLimitFilter implements Filter {

  private final String path;
  private final int capacity;
  private final long windowNanos;
  private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  public RateLimitFilter(RateLimitProperties properties) {
    this.path = properties.getPath();
    this.capacity = properties.getCapacity();
    this.windowNanos = properties.getWindow().toNanos();
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletResponse httpResponse = (HttpServletResponse) response;
    String servletPath = ((jakarta.servlet.http.HttpServletRequest) request).getServletPath();
    String method = ((jakarta.servlet.http.HttpServletRequest) request).getMethod();

    if (!path.equals(servletPath) || !"POST".equalsIgnoreCase(method)) {
      chain.doFilter(request, response);
      return;
    }

    String key = request.getRemoteAddr();
    Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, windowNanos));
    if (bucket.tryConsume()) {
      chain.doFilter(request, response);
    } else {
      httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      httpResponse.setContentType("application/json;charset=UTF-8");
      httpResponse.getWriter().write("{\"message\":\"操作过于频繁，请稍后再试\"}");
    }
  }

  private static final class Bucket {
    private final int capacity;
    private final long windowNanos;
    private long tokens;
    private long refillTime;

    Bucket(int capacity, long windowNanos) {
      this.capacity = capacity;
      this.windowNanos = windowNanos;
      this.tokens = capacity;
      this.refillTime = System.nanoTime();
    }

    synchronized boolean tryConsume() {
      long now = System.nanoTime();
      long elapsed = now - refillTime;
      if (elapsed >= windowNanos) {
        tokens = capacity;
        refillTime = now;
      }
      if (tokens > 0) {
        tokens--;
        return true;
      }
      return false;
    }
  }
}
