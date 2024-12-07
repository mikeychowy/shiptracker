package com.example.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties(TeqplayConfiguration.TEQPLAY_PREFIX)
@Requires(property = TeqplayConfiguration.TEQPLAY_PREFIX)
public record TeqplayConfiguration(@NotNull String username, @NotNull String password) {
  public static final String TEQPLAY_PREFIX = "teqplay";
}
