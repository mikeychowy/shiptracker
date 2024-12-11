package com.example.repository;

import com.example.entity.ShipEntity;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;
import java.util.Collection;
import java.util.Optional;
import org.bson.types.ObjectId;

@MongoRepository(databaseName = "teqplay")
public interface ShipRepository extends CrudRepository<ShipEntity, ObjectId> {

  @NonNull Optional<ShipEntity> findByMmsi(@NonNull String mmsi);

  @NonNull Collection<ShipEntity> findByIsInPort(Boolean inPort);
}
