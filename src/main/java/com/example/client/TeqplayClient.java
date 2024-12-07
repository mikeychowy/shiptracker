package com.example.client;

import com.example.config.TeqplayConfiguration;
import com.example.dto.response.TeqplayLoginResponse;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.retry.annotation.Retryable;
import jakarta.validation.constraints.NotBlank;
import org.reactivestreams.Publisher;

@Client(id = TeqplayConfiguration.TEQPLAY_PREFIX)
@Retryable
public interface TeqplayClient {

  @Post("/auth/login")
  @SingleResult
  Publisher<TeqplayLoginResponse> login(@NotBlank String username, @NotBlank String password);
}
