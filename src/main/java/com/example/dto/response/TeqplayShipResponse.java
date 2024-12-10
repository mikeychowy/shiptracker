package com.example.dto.response;

import com.example.entity.LocationPart;
import io.micronaut.core.annotation.Nullable;
import java.util.Map;
import lombok.Data;

@Data
public final class TeqplayShipResponse {

  private String mmsi;

  private Long timeLastUpdate;

  @Nullable private Double courseOverGround;

  @Nullable private String destination;

  @Nullable private Double speedOverGround;

  @Nullable private Map<String, Object> extras;

  private String callSign;

  @Nullable private String imoNumber;

  @Nullable private String name;

  @Nullable private String shipType;

  @Nullable private String trueDestination;

  @Nullable private LocationPart location;

  @Nullable private Integer coms;

  @Nullable private String status;
}
