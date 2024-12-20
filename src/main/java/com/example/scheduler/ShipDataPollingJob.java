package com.example.scheduler;

import com.example.client.TeqplayClient;
import com.example.exception.BusinessException;
import com.example.service.ShipDataService;
import com.example.utils.JsonFileUtils;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@Requires(beans = TeqplayClient.class)
public final class ShipDataPollingJob {

  private final TeqplayClient teqplayClient;
  private final ShipDataService shipDataService;
  private final ExecutorService virtualThreadPerTaskExecutor;

  @Inject
  public ShipDataPollingJob(
      TeqplayClient teqplayClient,
      ShipDataService shipDataService,
      @Named("virtualThreadPerTaskExecutorService") ExecutorService virtualThreadPerTaskExecutor) {
    this.teqplayClient = teqplayClient;
    this.shipDataService = shipDataService;
    this.virtualThreadPerTaskExecutor = virtualThreadPerTaskExecutor;
  }

  @Scheduled(initialDelay = "10s", fixedDelay = "1m")
  @ExecuteOn(TaskExecutors.SCHEDULED)
  public void execute() {
    // create a temporary json file
    File tmpJsonFile = JsonFileUtils.createTempJsonFile();
    try (FileOutputStream fos = new FileOutputStream(tmpJsonFile);
        ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      // write the byte array to the file
      teqplayClient.retrieveLatestShipDataAndStreamToOutput(fos);
    } catch (IOException e) {
      throw new BusinessException(e);
    }

    // let the service handle the parsing in another virtual thread
    virtualThreadPerTaskExecutor.submit(() -> shipDataService.handlePollingData(tmpJsonFile));
  }
}
