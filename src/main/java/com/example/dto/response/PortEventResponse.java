package com.example.dto.response;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected
public record PortEventResponse(ShipResponse ship, String event, Long timeOfEvent) {}
