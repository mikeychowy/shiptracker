package com.example.repository;

import com.example.entity.ShipEntity;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;
import java.util.Collection;
import java.util.List;
import org.bson.types.ObjectId;

@MongoRepository
public interface ShipRepository extends CrudRepository<ShipEntity, ObjectId> {

  @NonNull Collection<ShipEntity> findByMmsiInList(@NonNull List<String> mmsiList);
}
