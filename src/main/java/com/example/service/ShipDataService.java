package com.example.service;

import com.example.constant.PortEvent;
import com.example.dto.response.TeqplayShipResponse;
import com.example.entity.LocationPart;
import com.example.entity.PortEventEntity;
import com.example.entity.ShipEntity;
import com.example.exception.BusinessException;
import com.example.repository.PortEventRepository;
import com.example.repository.ShipRepository;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
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
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

@Slf4j
@Singleton
public final class ShipDataService {
  private final ShipRepository shipRepository;
  private final PortEventRepository portEventRepository;
  private final ObjectMapper objectMapper;
  private final Polygon rotterdamPortPolygon;
  private final ExecutorService virtualThreadPerTaskExecutor;

  @Inject
  public ShipDataService(
      ShipRepository shipRepository,
      PortEventRepository portEventRepository,
      ObjectMapper objectMapper,
      @Named("rotterdamPortPolygon") Polygon rotterdamPortPolygon,
      @Named("virtualThreadPerTaskExecutorService") ExecutorService virtualThreadPerTaskExecutor) {
    this.shipRepository = shipRepository;
    this.portEventRepository = portEventRepository;
    this.objectMapper = objectMapper;
    this.rotterdamPortPolygon = rotterdamPortPolygon;
    this.virtualThreadPerTaskExecutor = virtualThreadPerTaskExecutor;
  }

  public void handlePollingData(@Nonnull File tmpJsonFile) {
    // ahead of read checks
    if (!tmpJsonFile.exists() || !tmpJsonFile.canRead()) {
      throw new BusinessException("File not found or readable: " + tmpJsonFile.getAbsolutePath());
    }

    try (FileInputStream stream = new FileInputStream(tmpJsonFile)) {
      var shipDataList =
          objectMapper.readValue(stream, Argument.listOf(TeqplayShipResponse.class)).stream()
              .filter(Objects::nonNull)
              .filter(response -> StringUtils.isNotBlank(response.getMmsi()))
              .toList();

      if (shipDataList.isEmpty()) {
        log.info("No ship data to be processed");
        return;
      }

      shipDataList.forEach(this::processResponse);
    } catch (IOException e) {
      log.error("IOException during handlePollingData", e);
      throw new BusinessException(e);
    }

    // finally, delete the temp file as the processing is done
    try {
      Files.delete(tmpJsonFile.toPath());
    } catch (IOException e) {
      log.error("Failed to delete temporary file: {}", tmpJsonFile.getAbsolutePath(), e);
    }
  }

  private void processResponse(TeqplayShipResponse teqplayShipResponse) {
    // since in JDK 21, there's no "Gatherers" yet, and streams can't take advantage of Virtual
    // Threads
    // that's why we do it manually
    virtualThreadPerTaskExecutor.submit(() -> {
      // we need this to flag whether the ship is inside port or not when creating new data in DB
      var isResponseInsidePolygon = checkIfLocationInsidePort(teqplayShipResponse.getLocation());

      var optionalShipEntity = shipRepository.findByMmsi(teqplayShipResponse.getMmsi());
      if (optionalShipEntity.isPresent()) {
        // check port event
        var shipEntity = optionalShipEntity.get();
        Instant responseUpdateTime = Instant.ofEpochMilli(teqplayShipResponse.getTimeLastUpdate());
        Instant dbUpdateTime = Instant.ofEpochMilli(shipEntity.getTimeLastUpdate());
        // only do the processing if the data from response is newer than DB
        if (dbUpdateTime.isBefore(responseUpdateTime)) {
          var isShipInsidePolygon =
              // check from flag, if null, check from location
              Optional.ofNullable(shipEntity.getIsInPort())
                  .orElse(checkIfLocationInsidePort(shipEntity.getLocation()));
          processPortEventLogic(
              isShipInsidePolygon, isResponseInsidePolygon, shipEntity, teqplayShipResponse);
        } else {
          // do nothing as this is not the most updated data, don't even save it into DB
          return;
        }
      }

      // save new ship response
      var shipEntity = ShipEntity.builder()
          .mmsi(teqplayShipResponse.getMmsi())
          .timeLastUpdate(teqplayShipResponse.getTimeLastUpdate())
          .extras(teqplayShipResponse.getExtras())
          .coms(teqplayShipResponse.getComs())
          .courseOverGround(teqplayShipResponse.getCourseOverGround())
          .speedOverGround(teqplayShipResponse.getSpeedOverGround())
          .callSign(teqplayShipResponse.getCallSign())
          .imoNumber(teqplayShipResponse.getImoNumber())
          .destination(teqplayShipResponse.getDestination())
          .trueDestination(teqplayShipResponse.getTrueDestination())
          .location(teqplayShipResponse.getLocation())
          .status(teqplayShipResponse.getStatus())
          .timeLastUpdate(teqplayShipResponse.getTimeLastUpdate())
          .shipType(teqplayShipResponse.getShipType())
          .name(teqplayShipResponse.getName())
          .build();
      shipRepository.save(shipEntity);
    });
  }

  private Boolean checkIfLocationInsidePort(@Nullable LocationPart location) {
    if (Objects.nonNull(location) && StringUtils.equalsIgnoreCase("point", location.getType())) {
      var coordinatesList = Optional.ofNullable(location.getCoordinates()).orElse(List.of());
      if (coordinatesList.size() == 2) {
        var point = new GeometryFactory()
            .createPoint(new Coordinate(coordinatesList.get(0), coordinatesList.get(1)));
        return rotterdamPortPolygon.contains(point);
      }
    }
    return false;
  }

  private void processPortEventLogic(
      Boolean isShipInsidePolygon,
      Boolean isResponseInsidePolygon,
      ShipEntity shipEntity,
      TeqplayShipResponse teqplayShipResponse) {

    if (Boolean.TRUE.equals(isShipInsidePolygon) && Boolean.FALSE.equals(isResponseInsidePolygon)) {
      // EXIT EVENT
      var portEvent = PortEventEntity.builder()
          .event(PortEvent.EXIT)
          .ship(shipEntity)
          .timeLastUpdate(teqplayShipResponse.getTimeLastUpdate())
          .build();
      savePortEvent(portEvent);
      shipEntity.setIsInPort(false);
    } else if (Boolean.FALSE.equals(isShipInsidePolygon)
        && Boolean.TRUE.equals(isResponseInsidePolygon)) {
      // ENTRY EVENT
      var portEvent = PortEventEntity.builder()
          .event(PortEvent.ENTRY)
          .ship(shipEntity)
          .timeLastUpdate(teqplayShipResponse.getTimeLastUpdate())
          .build();
      savePortEvent(portEvent);
      shipEntity.setIsInPort(true);
    }
    // finally, regardless if a port event occurs or not, update the data
    shipEntity
        .setExtras(teqplayShipResponse.getExtras())
        .setComs(teqplayShipResponse.getComs())
        .setCourseOverGround(teqplayShipResponse.getCourseOverGround())
        .setSpeedOverGround(teqplayShipResponse.getSpeedOverGround())
        .setCallSign(teqplayShipResponse.getCallSign())
        .setImoNumber(teqplayShipResponse.getImoNumber())
        .setDestination(teqplayShipResponse.getDestination())
        .setTrueDestination(teqplayShipResponse.getTrueDestination())
        .setLocation(teqplayShipResponse.getLocation())
        .setStatus(teqplayShipResponse.getStatus())
        .setTimeLastUpdate(teqplayShipResponse.getTimeLastUpdate())
        .setShipType(teqplayShipResponse.getShipType())
        .setName(teqplayShipResponse.getName());
    shipRepository.update(shipEntity);
  }

  private void savePortEvent(PortEventEntity portEventEntity) {
    portEventRepository.save(portEventEntity);
  }
}
