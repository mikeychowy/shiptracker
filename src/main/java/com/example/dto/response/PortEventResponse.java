package com.example.dto.response;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected
public record PortEventResponse(String mmsi, String event, Long timeLastUpdate) {}
