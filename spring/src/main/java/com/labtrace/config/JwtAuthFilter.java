package com.labtrace.config;

import com.labtrace.util.SecurityUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.labtrace.model.User;
import com.labtrace.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final UserRepository userRepository;

  public JwtAuthFilter(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = header.substring(7);
    try {
      Claims claims = Jwts.parserBuilder()
          .setSigningKey(SecurityUtil.signingKey())
          .build()
          .parseClaimsJws(token)
          .getBody();

      Long userId = claims.get("id", Long.class);
      User user = userRepository.findById(userId)
          .orElse(null);

      if (user == null) {
        filterChain.doFilter(request, response);
        return;
      }

      UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
          user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
      );
      auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(auth);
    } catch (Exception e) {
      SecurityContextHolder.clearContext();
    }
    filterChain.doFilter(request, response);
  }
}
