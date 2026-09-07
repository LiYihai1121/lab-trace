package com.labtrace.dto.response;

public record LoginResponse(String token, UserResponse user) {
}
