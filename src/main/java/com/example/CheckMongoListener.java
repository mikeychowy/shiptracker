package com.example;

import com.example.entity.LocationPart;
import com.example.entity.ShipEntity;
import com.example.repository.ShipRepository;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class CheckMongoListener {

  private final ShipRepository shipRepository;

  @Inject
  public CheckMongoListener(ShipRepository shipRepository) {
    this.shipRepository = shipRepository;
  }

  @EventListener
  public void onStartupEvent(StartupEvent event) {
    var shipInDB = shipRepository
        .findByMmsi("test1")
        .orElseGet(() -> shipRepository.save(ShipEntity.builder()
            .mmsi("test1")
            .name("testShip")
            .status("NO_STATUS")
            .location(LocationPart.builder()
                .type("Point")
                .coordinates(List.of(24.04456, 456.66552))
                .build())
            .build()));
    log.debug("test ship: {}", shipInDB);
    log.info("connection successful");
  }
}
