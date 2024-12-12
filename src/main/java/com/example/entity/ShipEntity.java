package com.example.entity;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Version;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@MappedEntity("ships_latest_data")
public class ShipEntity {

  @Id
  @NonNull private String id;

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

  private Boolean isInPort;

  @Version
  private Long version;

  @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "ship")
  @Nullable private List<PortEventEntity> portEvents;
}
