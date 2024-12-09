package com.example.entity;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Version;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@MappedEntity("ships_latest_data")
public class ShipEntity {

  @Id
  @GeneratedValue
  private ObjectId id;

  @NotNull private String mmsi;

  @NotNull private Long timeLastUpdate;

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

  private boolean isInPort = false;

  @Version
  private Long version;

  @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "ship")
  @Nullable private List<PortEventEntity> portEvents;

  @Data
  public static class Location {
    @Nullable private List<Double> coordinates;

    @Nullable private String type;
  }
}
