package com.example.repository;

import com.example.entity.PortEventEntity;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;
import java.util.Collection;
import java.util.List;

@MongoRepository(databaseName = "teqplay")
public interface PortEventRepository extends CrudRepository<PortEventEntity, String> {
  @Join("ship")
  @NonNull Collection<PortEventEntity> findByEvent(String portEvent);

  @Join("ship")
  @NonNull List<PortEventEntity> findAll();
}
