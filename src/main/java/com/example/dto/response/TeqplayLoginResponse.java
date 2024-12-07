package com.example.dto.response;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record TeqplayLoginResponse(
    String createdAt,
    String userName,
    Integer expiresInSeconds,
    String refreshToken,
    String token) {}
