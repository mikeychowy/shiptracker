package com.example.dto.response;

import jakarta.annotation.Nullable;
import java.util.List;
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

  @Nullable private Location location;

  @Nullable private Integer coms;

  @Nullable private String status;

  @Data
  public static class Location {
    @Nullable private List<Double> coordinates;

    @Nullable private String type;
  }
}
