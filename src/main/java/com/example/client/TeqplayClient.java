package com.example.client;

import com.example.config.TeqplayConfiguration;
import com.example.dto.request.TeqplayLoginRequest;
import com.example.dto.response.TeqplayLoginResponse;
import io.micronaut.cache.caffeine.DefaultSyncCache;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Requires(beans = TeqplayConfiguration.class)
public final class TeqplayClient {
  private static final String TOKEN_CACHE_KEY = "teqplay";

  private final HttpClient httpClient;
  private final DefaultSyncCache cache;
  private final TeqplayConfiguration teqplayConfiguration;

  @Inject
  public TeqplayClient(
      @Client("teqplay") HttpClient httpClient, DefaultSyncCache cache, TeqplayConfiguration teqplayConfiguration) {
    this.httpClient = httpClient;
    this.cache = cache;
    this.teqplayConfiguration = teqplayConfiguration;
  }

  @ExecuteOn(TaskExecutors.VIRTUAL)
  public String login() {
    // get from cache first
    return cache.get(TOKEN_CACHE_KEY, String.class)
        // if not exists, then we get a new token
        .orElseGet(() -> {
          HttpRequest<TeqplayLoginRequest> request = HttpRequest.POST(
                  "/auth/login",
                  new TeqplayLoginRequest(teqplayConfiguration.username(), teqplayConfiguration.password()))
              .accept(MediaType.APPLICATION_JSON)
              .contentType(MediaType.APPLICATION_JSON);
          // since we execute this in a virtual thread anyway, let's just use blocking client
          var token = httpClient.toBlocking().retrieve(request, TeqplayLoginResponse.class).token();
          cache.put(TOKEN_CACHE_KEY, token);
          return token;
        });
  }
}
