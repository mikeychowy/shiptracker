package com.example.service;

import com.example.constant.PortEvent;
import com.example.dto.response.PortEventResponse;
import com.example.dto.response.ShipResponse;
import com.example.dto.response.TeqplayShipResponse;
import com.example.entity.LocationPart;
import com.example.entity.PortEventEntity;
import com.example.entity.ShipEntity;
import com.example.exception.BusinessException;
import com.example.repository.PortEventRepository;
import com.example.repository.ShipRepository;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

@Slf4j
@Singleton
@Requires(beans = {ShipRepository.class, PortEventRepository.class})
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
    log.info("start handling polling ship data");
    // ahead of read checks
    if (!tmpJsonFile.exists() || !tmpJsonFile.canRead()) {
      throw new BusinessException("File not found or readable: " + tmpJsonFile.getAbsolutePath());
    }

    try (FileInputStream stream = new FileInputStream(tmpJsonFile);
        JsonParser jsonParser = objectMapper.getFactory().createParser(stream)) {
      if (!jsonParser.nextToken().equals(JsonToken.START_ARRAY)) {
        throw new BusinessException("Unexpected token: " + jsonParser.getCurrentToken());
      }

      // atomic counter for, well, counting, consume around 2000 data
      AtomicInteger counter = new AtomicInteger(0);
      // initial holder
      List<TeqplayShipResponse> responseList = new ArrayList<>();

      // this is just to simulate a more sophisticated queue system like Kafka or NATS.IO
      // since this doesn't have backpressure, retry, indexing/offsets
      // but the JSON is BIG (like my appetite 😋), so we need to use Jackson's streaming API
      // well, this makes it a bit off from "real-time" data,
      // but I think the time margin of 1 minute can still be achieved
      // in a real world scenario, this is more likely an independent listener
      // and will receive the data piece by piece instead of putting them in a file
      // then reading it like this
      while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
        TeqplayShipResponse response =
            objectMapper.readValue(jsonParser, TeqplayShipResponse.class);
        responseList.add(response);
        var currentCount = counter.incrementAndGet();
        if (currentCount >= 1999) {
          // handle 2000 data at a time, with some margin for counting error
          virtualThreadPerTaskExecutor.submit(() -> {
            // to avoid concurrency shenanigans, since we would need to clear the holder
            var copyList = List.copyOf(responseList);
            copyList.forEach(this::processResponse);
          });
          responseList.clear();
          counter.set(0);
        }
      }

      // since we might miss out on the rest of the data
      // if the end of the tokens are less than 2000
      if (counter.get() != 0 && !responseList.isEmpty()) {
        virtualThreadPerTaskExecutor.submit(() -> {
          // to let the initial holder be garbage collected
          var copyList = List.copyOf(responseList);
          copyList.forEach(this::processResponse);
        });
      }
    } catch (IOException e) {
      log.error("json processing error during handlePollingData", e);
      throw new BusinessException("json processing error", e);
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
    log.info("start processing ship entry/exit port event");
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
          .isInPort(isResponseInsidePolygon)
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
      log.info("an exit event has occurred for ship with mmsi: {}", shipEntity.getMmsi());
      // EXIT EVENT
      var portEvent = PortEventEntity.builder()
          .event(PortEvent.EXIT.name())
          .ship(shipEntity)
          .timeLastUpdate(teqplayShipResponse.getTimeLastUpdate())
          .build();
      savePortEvent(portEvent);
      shipEntity.setIsInPort(false);
    } else if (Boolean.FALSE.equals(isShipInsidePolygon)
        && Boolean.TRUE.equals(isResponseInsidePolygon)) {
      log.info("an entry event has occurred for ship with mmsi: {}", shipEntity.getMmsi());
      // ENTRY EVENT
      var portEvent = PortEventEntity.builder()
          .event(PortEvent.ENTRY.name())
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

  public List<PortEventResponse> findPortEvents(@Nullable PortEvent portEvent) {
    if (Objects.isNull(portEvent)) {
      return portEventRepository.findAll().stream()
          .filter(Objects::nonNull)
          .map(entity -> new PortEventResponse(
              entity.getShip().getMmsi(), entity.getEvent(), entity.getTimeLastUpdate()))
          .toList();
    }

    return portEventRepository.findByEvent(portEvent.name()).stream()
        .filter(Objects::nonNull)
        .map(entity -> new PortEventResponse(
            entity.getShip().getMmsi(), entity.getEvent(), entity.getTimeLastUpdate()))
        .toList();
  }

  public List<ShipResponse> findAllShipsInPort() {
    var ships = shipRepository.findByIsInPort(true);
    return List.copyOf(ships).stream()
        .map(ship -> ShipResponse.builder()
            .mmsi(ship.getMmsi())
            .timeLastUpdate(ship.getTimeLastUpdate())
            .courseOverGround(ship.getCourseOverGround())
            .speedOverGround(ship.getSpeedOverGround())
            .destination(ship.getDestination())
            .trueDestination(ship.getTrueDestination())
            .callSign(ship.getCallSign())
            .imoNumber(ship.getImoNumber())
            .location(ship.getLocation())
            .coms(ship.getComs())
            .extras(ship.getExtras())
            .status(ship.getStatus())
            .shipType(ship.getShipType())
            .isInPort(ship.getIsInPort())
            .build())
        .toList();
  }
}
