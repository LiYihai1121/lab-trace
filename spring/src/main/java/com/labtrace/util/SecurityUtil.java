package com.labtrace.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;

public final class SecurityUtil {

  public static final int BCRYPT_ROUNDS = 10;
  public static final String JWT_SECRET = System.getenv("JWT_SECRET") != null
      ? System.getenv("JWT_SECRET")
      : "lab-trace-dev-secret-change-in-production";
  public static final long JWT_TTL_MS = 24 * 60 * 60 * 1000;

  private SecurityUtil() {
  }

  public static Key signingKey() {
    return Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
  }

  public static String buildToken(Long id, String username, String name, String role) {
    return Jwts.builder()
        .claim("id", id)
        .claim("username", username)
        .claim("name", name)
        .claim("role", role)
        .setIssuedAt(new java.util.Date())
        .setExpiration(new java.util.Date(System.currentTimeMillis() + JWT_TTL_MS))
        .signWith(signingKey())
        .compact();
  }

  public static String dummyRaw() {
    return java.util.UUID.randomUUID().toString();
  }
}
