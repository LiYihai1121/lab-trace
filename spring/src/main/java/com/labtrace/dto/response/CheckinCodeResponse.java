package com.labtrace.dto.response;

public record CheckinCodeResponse(String code, long expiresAt, int expiresIn) {
}
