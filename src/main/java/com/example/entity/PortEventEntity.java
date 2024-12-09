package com.example.entity;

import com.example.constant.PortEvent;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.annotation.sql.JoinColumn;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@MappedEntity("port_events")
public class PortEventEntity {

  @Id
  @GeneratedValue
  private ObjectId id;

  @Relation(value = Relation.Kind.MANY_TO_ONE)
  @JoinColumn(name = "mmsi")
  @NotNull private ShipEntity ship;

  @NotNull private PortEvent event;

  @NotNull private Long timeLastUpdate;

  @Version
  private Long version;
}
