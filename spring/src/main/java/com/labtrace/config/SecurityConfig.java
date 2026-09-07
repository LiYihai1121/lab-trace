package com.labtrace.config;

import com.labtrace.util.SecurityUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.labtrace.model.User;
import com.labtrace.repository.PasswordResetTokenRepository;
import com.labtrace.repository.UserRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**", "/api/health").permitAll()
            .requestMatchers("/api/qrcode/**").hasRole("ADMIN")
            .requestMatchers("/api/users/**").hasRole("ADMIN")
            .requestMatchers("/api/stats/**").hasRole("ADMIN")
            .requestMatchers("/api/checkin/**", "/api/records/**").authenticated()
            .anyRequest().denyAll()
        )
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(SecurityUtil.BCRYPT_ROUNDS);
  }

  @Bean
  public CommandLineRunner init(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                org.springframework.core.env.Environment env) {
    return args -> {
      boolean createDefault = !"false".equalsIgnoreCase(env.getProperty("CREATE_DEFAULT_ADMIN", "true"));
      if (createDefault && userRepository.count() == 0) {
        User user = new User();
        user.setUsername("admin");
        String defaultPassword = env.getProperty("ADMIN_PASSWORD", "admin123");
        user.setPasswordHash(passwordEncoder.encode(defaultPassword));
        user.setName("系统管理员");
        user.setRole(User.Role.admin);
        user.setCreatedAt(com.labtrace.util.DateTimeUtil.now());
        userRepository.save(user);
      }
    };
  }
}
