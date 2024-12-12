package com.example.entity;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.annotation.sql.JoinColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@MappedEntity("port_events")
public class PortEventEntity {

  @Id
  private String id;

  @JoinColumn(name = "shipId", referencedColumnName = "mmsi")
  @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.NONE)
  @NonNull private ShipEntity ship;

  @NonNull private String event;

  @NonNull private Long timeLastUpdate;

  @Version
  private Long version;
}
