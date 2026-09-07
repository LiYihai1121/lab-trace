package com.labtrace.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

  @Bean
  public FilterRegistrationBean<CorsFilter> corsFilter(RateLimitProperties rateLimitProperties) {
    CorsConfiguration config = new CorsConfiguration();
    String origins = System.getenv("CORS_ORIGIN");
    if (origins != null && !origins.isBlank()) {
      for (String origin : origins.split(",")) {
        config.addAllowedOrigin(origin.trim());
      }
    } else {
      config.addAllowedOriginPattern("*");
    }
    config.setAllowCredentials(true);
    config.addAllowedHeader("*");
    config.addAllowedMethod("*");

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);

    FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return bean;
  }
}
