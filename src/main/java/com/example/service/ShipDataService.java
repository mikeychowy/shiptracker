package com.example.service;

import com.example.dto.response.TeqplayShipResponse;
import com.example.entity.ShipEntity;
import com.example.exception.BusinessException;
import com.example.repository.PortEventRepository;
import com.example.repository.ShipRepository;
import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.type.Argument;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.locationtech.jts.geom.Polygon;

@Slf4j
@Singleton
public final class ShipDataService {
  private final ShipRepository shipRepository;
  private final PortEventRepository portEventRepository;
  private final ObjectMapper objectMapper;
  private final Polygon rotterdamPortPolygon;

  @Inject
  public ShipDataService(
      ShipRepository shipRepository,
      PortEventRepository portEventRepository,
      ObjectMapper objectMapper,
      @Named("rotterdamPortPolygon") Polygon rotterdamPortPolygon) {
    this.shipRepository = shipRepository;
    this.portEventRepository = portEventRepository;
    this.objectMapper = objectMapper;
    this.rotterdamPortPolygon = rotterdamPortPolygon;
  }

  @Blocking
  @ExecuteOn(TaskExecutors.VIRTUAL)
  public void handlePollingData(@Nonnull File tmpJsonFile) {
    // ahead of read checks
    if (!tmpJsonFile.exists() || !tmpJsonFile.canRead()) {
      throw new BusinessException("File not found or readable: " + tmpJsonFile.getAbsolutePath());
    }

    try (FileInputStream stream = new FileInputStream(tmpJsonFile)) {
      var shipDataList =
          objectMapper.readValue(stream, Argument.listOf(TeqplayShipResponse.class)).stream()
              .filter(Objects::nonNull)
              .toList();

      if (shipDataList.isEmpty()) {
        log.info("No ship data to be processed");
        return;
      }

      var shipIds = shipDataList.stream().map(TeqplayShipResponse::getMmsi).toList();
      var shipsInDB = List.copyOf(shipRepository.findByMmsiInList(shipIds));
      compareDataThenProcess(shipsInDB, shipDataList);

      // save the most updated ship data
    } catch (IOException e) {
      throw new BusinessException(e);
    }

    // finally, delete the temp file as the processing is done
    try {
      Files.delete(tmpJsonFile.toPath());
    } catch (IOException e) {
      log.error("Failed to delete temporary file: {}", tmpJsonFile.getAbsolutePath());
    }
  }

  private void compareDataThenProcess(List<ShipEntity> shipsInDB, List<TeqplayShipResponse> shipDataResponse) {
    if (!shipsInDB.isEmpty()) {
      log.info("Comparing Ship data from DB and response");
      var portEvents = shipDataResponse.stream().parallel().map(teqplayShipResponse -> {
            var maybeShip = shipsInDB.stream()
                .filter(Objects::nonNull)
                .filter(
                    ship -> StringUtils.equalsIgnoreCase(teqplayShipResponse.getMmsi(), ship.getMmsi()))
                .findFirst();
            if (maybeShip.isPresent()) {
              // only process the existing ships, since if it doesn't exist,
              // that means the saved data will be initializer
              var targetShip = maybeShip.get();
              Instant responseUpdateTime = Instant.ofEpochMilli(teqplayShipResponse.getTimeLastUpdate());
              Instant dbUpdateTime = Instant.ofEpochMilli(targetShip.getTimeLastUpdate());
              if (dbUpdateTime.isBefore(responseUpdateTime)) {
                // only do the processing if the data from response is newer than DB
                processPortEvents(targetShip, teqplayShipResponse);
              }
            }

            return null;
          })
          .filter(Objects::nonNull)
          .toList();

      if (!portEvents.isEmpty()) {
        // save to DB
        log.info("Saving port events to DB");
        log.debug("port events: {}", portEvents);
      }
    }
  }

  private void processPortEvents(ShipEntity entity, TeqplayShipResponse teqplayShipResponse) {
    Optional.ofNullable(entity.getLocation()).map(ShipEntity.Location::getCoordinates);
  }
}
