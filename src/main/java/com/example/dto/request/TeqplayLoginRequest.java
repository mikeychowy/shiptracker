package com.example.dto.request;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record TeqplayLoginRequest(
    String username,
    String password) {}
