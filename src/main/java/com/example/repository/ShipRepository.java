package com.example.repository;

import com.example.entity.ShipEntity;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@MongoRepository(databaseName = "teqplay")
public interface ShipRepository extends CrudRepository<ShipEntity, String> {

  @NonNull Collection<ShipEntity> findByMmsiInList(@NonNull List<String> idList);

  @NonNull Optional<ShipEntity> findByMmsi(@NonNull String mmsi);

  @NonNull Collection<ShipEntity> findByIsInPort(Boolean inPort);
}
