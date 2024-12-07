package com.example.client;

import com.example.config.TeqplayConfiguration;
import com.example.dto.response.TeqplayLoginResponse;
import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.retry.annotation.Retryable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.constraints.NotBlank;

@Client(id = TeqplayConfiguration.TEQPLAY_PREFIX)
@Retryable
public interface TeqplayClient {

  @Post("/auth/login")
  @ExecuteOn(TaskExecutors.VIRTUAL)
  @Blocking
  @SingleResult
  TeqplayLoginResponse login(@NotBlank String username, @NotBlank String password);
}
