package com.example.controller;

import com.example.constant.PortEvent;
import com.example.dto.response.PortEventResponse;
import com.example.dto.response.ShipResponse;
import com.example.service.ShipDataService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import jakarta.inject.Inject;
import java.util.List;

@Controller("/api/v1/ship")
public class ShipController {
  private final ShipDataService shipDataService;

  @Inject
  public ShipController(ShipDataService shipDataService) {
    this.shipDataService = shipDataService;
  }

  @Get("/portEvents")
  public HttpResponse<List<PortEventResponse>> getPortEvents(
      @QueryValue @Nullable PortEvent portEvent) {
    return HttpResponse.ok().body(shipDataService.findPortEvents(portEvent));
  }

  @Get("/inPort")
  public HttpResponse<List<ShipResponse>> getShipsInPort() {
    return HttpResponse.ok().body(shipDataService.findAllShipsInPort());
  }
}
