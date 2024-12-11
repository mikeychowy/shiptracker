package com.example.repository;

import com.example.entity.PortEventEntity;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;
import org.bson.types.ObjectId;

@MongoRepository(databaseName = "teqplay")
public interface PortEventRepository extends CrudRepository<PortEventEntity, ObjectId> {
  List<PortEventEntity> findByEvent(String portEvent);
}
