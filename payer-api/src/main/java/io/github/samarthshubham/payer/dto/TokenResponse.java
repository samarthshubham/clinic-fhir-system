package io.github.samarthshubham.payer.dto;

public record TokenResponse(
    String accessToken, String refreshToken, String tokenType, long expiresIn, AuthUser user) {}
