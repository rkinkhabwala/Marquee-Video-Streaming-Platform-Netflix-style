package com.marquee.api.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        Long userId,
        String email,
        String role) {
}
