package com.labtrace.dto.response;

import com.labtrace.model.User;

public record UserResponse(Long id, String username, String name, String role, String createdAt) {

  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getUsername(),
        user.getName(),
        user.getRole().name(),
        user.getCreatedAt()
    );
  }
}
