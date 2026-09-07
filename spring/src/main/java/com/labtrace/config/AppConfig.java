package com.labtrace.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.labtrace.util.SecurityUtil;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class AppConfig {

  @Bean
  public BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(SecurityUtil.BCRYPT_ROUNDS);
  }
}
