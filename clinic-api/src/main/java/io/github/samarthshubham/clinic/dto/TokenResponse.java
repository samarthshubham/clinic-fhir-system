package io.github.samarthshubham.clinic.dto;

public record TokenResponse(
    String accessToken, String refreshToken, String tokenType, long expiresIn, AuthUser user) {}
