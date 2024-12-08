package com.example.client;

import com.example.config.TeqplayConfiguration;
import io.micronaut.cache.annotation.CacheConfig;
import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@CacheConfig("teqplay")
public final class TeqplayClient {
  private final HttpClient httpClient;
  private final TeqplayConfiguration teqplayConfiguration;

  @Inject
  public TeqplayClient(
      @Client("teqplay") HttpClient httpClient, TeqplayConfiguration teqplayConfiguration) {
    this.httpClient = httpClient;
    this.teqplayConfiguration = teqplayConfiguration;
  }

  @ExecuteOn(TaskExecutors.VIRTUAL)
  @Cacheable
  public void login() {
    // since we execute this in a virtual thread anyway, let's just block
    var blockingClient = httpClient.toBlocking();
  }
}
