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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.collector.Collectors2;
import org.eclipse.collections.impl.tuple.Tuples;
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

      // atomic counter for, well, counting
      AtomicInteger counter = new AtomicInteger(0);
      // initial holder
      MutableList<TeqplayShipResponse> responseList = Lists.mutable.of();

      // this is just to simulate a more sophisticated queue system like Kafka or NATS.IO
      // since this doesn't have backpressure, retry, indexing or offsets
      // but the JSON is BIG (like my appetite 😋), so we need to use Jackson's streaming API
      // well, this makes it a bit off from "real-time" data,
      // but I think the time margin of 1 minute can still be achieved
      // in a real world scenario, this is more likely an independent listener
      // and will receive the data piece(s) by piece(s) instead of putting them in a file
      // then reading it like this
      while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
        TeqplayShipResponse response =
            objectMapper.readValue(jsonParser, TeqplayShipResponse.class);
        responseList.add(response);

        var currentCount = counter.incrementAndGet();
        if (currentCount >= 9999) {
          responseList = responseList.select(Objects::nonNull);
          // handle 10K data at a time, with some margin for counting error
          // to avoid concurrency shenanigans, since we would need to clear the holder
          // besides, this ensures immutability
          processResponses(Lists.immutable.withAll(responseList));
          responseList.clear();
          counter.set(0);
        }
      }

      // since we might miss out on the rest of the data
      // if the end of the tokens are less than 10K
      if (counter.get() != 0 && !responseList.isEmpty()) {
        responseList = responseList.select(Objects::nonNull);
        processResponses(Lists.immutable.withAll(responseList));
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

    log.info("finished handling polling ship data");
  }

  private void processResponses(ImmutableList<TeqplayShipResponse> responses) {
    virtualThreadPerTaskExecutor.submit(() -> {
      ImmutableList<ShipEntity> shipsInDB =
          Lists.immutable.withAll(shipRepository.findByMmsiInList(responses.stream()
              .map(TeqplayShipResponse::getMmsi)
              .filter(Objects::nonNull)
              .collect(Collectors2.toList())));

      // for ease of filtering, we need only the mmsi of the ships in DB (if any)
      ImmutableList<String> shipsInDbMmsiList =
          shipsInDB.stream().map(ShipEntity::getMmsi).collect(Collectors2.toImmutableList());

      processNewShipData(responses, shipsInDbMmsiList);

      ImmutableList<Pair<ShipEntity, PortEventEntity>> shipUpdatePortEventPersistList = responses
          .select(res -> shipsInDbMmsiList.contains(res.getMmsi()))
          .collect(res -> {
            var isResponseInsidePolygon = checkIfLocationInsidePort(res.getLocation());
            var shipEntity = shipsInDB
                .select(ship -> StringUtils.equalsIgnoreCase(res.getMmsi(), ship.getMmsi()))
                .getOnly();
            Instant responseUpdateTime = Instant.ofEpochMilli(res.getTimeLastUpdate());
            Instant dbUpdateTime = Instant.ofEpochMilli(shipEntity.getTimeLastUpdate());

            // only do the processing if the data from response is newer than DB
            if (dbUpdateTime.isBefore(responseUpdateTime)) {
              var isShipInsidePolygon =
                  // check from flag, if null, check from location
                  Optional.ofNullable(shipEntity.getIsInPort())
                      .orElse(checkIfLocationInsidePort(shipEntity.getLocation()));
              return processPortEventLogic(
                  isShipInsidePolygon, isResponseInsidePolygon, shipEntity, res);
            }
            // do nothing as this is not the most updated data, don't even save it into DB
            return null;
          })
          .select(Objects::nonNull);

      processShipUpdatesAndPortEvents(shipUpdatePortEventPersistList);
    });
  }

  private void processNewShipData(
      ImmutableList<TeqplayShipResponse> responses, ImmutableList<String> shipsInDbMmsiList) {
    // get the responses that needs persisting first (new ships)
    ImmutableList<ShipEntity> responsesNeedPersisting = responses
        .reject(res -> shipsInDbMmsiList.contains(res.getMmsi()))
        .collect(res -> {
          // we need this to flag whether the ship is inside port or not when creating new data
          var isResponseInsidePolygon = checkIfLocationInsidePort(res.getLocation());
          return ShipEntity.builder()
              .id(res.getMmsi())
              .mmsi(res.getMmsi())
              .timeLastUpdate(res.getTimeLastUpdate())
              .extras(res.getExtras())
              .coms(res.getComs())
              .courseOverGround(res.getCourseOverGround())
              .speedOverGround(res.getSpeedOverGround())
              .callSign(res.getCallSign())
              .imoNumber(res.getImoNumber())
              .destination(res.getDestination())
              .trueDestination(res.getTrueDestination())
              .location(res.getLocation())
              .status(res.getStatus())
              .timeLastUpdate(res.getTimeLastUpdate())
              .shipType(res.getShipType())
              .name(res.getName())
              .isInPort(isResponseInsidePolygon)
              .build();
        });
    try {
      shipRepository.saveAll(responsesNeedPersisting);
    } catch (Exception e) {
      final String message = "failed to persist ships into DB";
      log.error(message, e);
      throw new BusinessException(message, e);
    }
  }

  private void processShipUpdatesAndPortEvents(
      ImmutableList<Pair<ShipEntity, PortEventEntity>> shipUpdatePortEventPersistList) {
    if (!shipUpdatePortEventPersistList.isEmpty()) {
      // get only the ship updates
      var shipUpdates =
          shipUpdatePortEventPersistList.collect(Pair::getOne).select(Objects::nonNull);
      if (!shipUpdates.isEmpty()) {
        // bulk update them
        try {
          shipRepository.updateAll(shipUpdates);
        } catch (Exception e) {
          final String message = "failed to update ships into DB";
          log.error(message, e);
          throw new BusinessException(message, e);
        }
      }

      // get the port events
      var portEvents =
          shipUpdatePortEventPersistList.collect(Pair::getTwo).select(Objects::nonNull);
      if (!portEvents.isEmpty()) {
        // bulk persist them
        try {
          portEventRepository.saveAll(portEvents);
        } catch (Exception e) {
          final String message = "failed to port events into DB";
          log.error(message, e);
          throw new BusinessException(message, e);
        }
      }
    }
  }

  @Nullable private Boolean checkIfLocationInsidePort(@Nullable LocationPart location) {
    if (Objects.nonNull(location) && StringUtils.equalsIgnoreCase("point", location.getType())) {
      var coordinatesList = Optional.ofNullable(location.getCoordinates()).orElse(List.of());
      if (coordinatesList.size() == 2) {
        var point = new GeometryFactory()
            .createPoint(new Coordinate(coordinatesList.get(0), coordinatesList.get(1)));
        return rotterdamPortPolygon.contains(point);
      }
    }
    return null;
  }

  private Pair<ShipEntity, PortEventEntity> processPortEventLogic(
      Boolean isShipInsidePolygon,
      Boolean isResponseInsidePolygon,
      ShipEntity shipEntity,
      TeqplayShipResponse teqplayShipResponse) {
    PortEventEntity portEvent = null;
    if (Boolean.TRUE.equals(isShipInsidePolygon) && Boolean.FALSE.equals(isResponseInsidePolygon)) {
      log.info("an exit event has occurred for ship with mmsi: {}", shipEntity.getMmsi());
      // EXIT EVENT
      portEvent = PortEventEntity.builder()
          .id(UUID.randomUUID().toString())
          .event(PortEvent.EXIT.name())
          .timeLastUpdate(teqplayShipResponse.getTimeLastUpdate())
          .build();
      shipEntity.setIsInPort(false);
    } else if (Boolean.FALSE.equals(isShipInsidePolygon)
        && Boolean.TRUE.equals(isResponseInsidePolygon)) {
      log.info("an entry event has occurred for ship with mmsi: {}", shipEntity.getMmsi());
      // ENTRY EVENT
      portEvent = PortEventEntity.builder()
          .id(UUID.randomUUID().toString())
          .event(PortEvent.ENTRY.name())
          .timeLastUpdate(teqplayShipResponse.getTimeLastUpdate())
          .build();
      shipEntity.setIsInPort(true);
    }
    // regardless if a port event occurs or not, update the data
    shipEntity
        .setId(shipEntity.getMmsi())
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

    if (Objects.nonNull(portEvent)) {
      portEvent.setShip(shipEntity);
    }

    return Tuples.pair(shipEntity, portEvent);
  }

  public List<PortEventResponse> findPortEvents(@Nullable PortEvent portEvent) {
    if (Objects.isNull(portEvent)) {
      return portEventRepository.findAll().stream()
          .filter(Objects::nonNull)
          .map(entity -> new PortEventResponse(
              this.mapShipEntityToResponse(entity.getShip()),
              entity.getEvent(),
              entity.getTimeLastUpdate()))
          .toList();
    }

    return portEventRepository.findByEvent(portEvent.name()).stream()
        .filter(Objects::nonNull)
        .map(entity -> new PortEventResponse(
            this.mapShipEntityToResponse(entity.getShip()),
            entity.getEvent(),
            entity.getTimeLastUpdate()))
        .toList();
  }

  public List<ShipResponse> findAllShipsInPort() {
    var ships = shipRepository.findByIsInPort(true);
    return List.copyOf(ships).stream().map(this::mapShipEntityToResponse).toList();
  }

  private ShipResponse mapShipEntityToResponse(@Nonnull ShipEntity ship) {
    return ShipResponse.builder()
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
        .build();
  }
}
