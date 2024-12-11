package com.example.dto.response;

import com.example.entity.LocationPart;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@Serdeable
@Introspected
@NoArgsConstructor
@AllArgsConstructor
public class ShipResponse {

  @NonNull private String mmsi;

  @NonNull private Long timeLastUpdate;

  @Nullable private Double courseOverGround;

  @Nullable private String destination;

  @Nullable private Double speedOverGround;

  @Nullable private Map<String, Object> extras;

  @Nullable private String callSign;

  @Nullable private String imoNumber;

  @Nullable private String name;

  @Nullable private String shipType;

  @Nullable private String trueDestination;

  @Nullable private LocationPart location;

  @Nullable private Integer coms;

  @Nullable private String status;

  @NonNull private Boolean isInPort;
}
