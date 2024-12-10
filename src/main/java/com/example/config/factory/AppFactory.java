package com.example.config.factory;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Factory
public final class AppFactory {

  @Singleton
  @Bean(typed = ExecutorService.class, preDestroy = "close")
  @Named("virtualThreadPerTaskExecutorService")
  ExecutorService virtualThreadPerTaskExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}
