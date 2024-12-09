package com.example.config.factory;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

@Factory
public final class JtsGeometryFactory {

  /**
   * [ [4.09365, 51.98509], [4.08719, 52.01616], [3.98969, 52.0345], [3.94652, 51.99088], [3.95805,
   * 51.9598], [3.98431, 51.91666], [4.46901, 51.82003], [4.55084, 51.64443], [4.629, 51.664],
   * [4.69875, 51.83797], [4.5382, 51.91703], [4.09365, 51.98509] ]
   */
  @Singleton
  @Bean(typed = Polygon.class)
  @Named("rotterdamPortPolygon")
  Polygon rotterdamPortPolygon() {
    return new GeometryFactory().createPolygon(new CoordinateArraySequence(new Coordinate[] {
      new Coordinate(4.09365, 51.98509),
      new Coordinate(4.08719, 52.01616),
      new Coordinate(3.98969, 52.0345),
      new Coordinate(3.94652, 51.99088),
      new Coordinate(3.95805, 51.9598),
      new Coordinate(3.98431, 51.91666),
      new Coordinate(4.46901, 51.82003),
      new Coordinate(4.55084, 51.64443),
      new Coordinate(4.629, 51.664),
      new Coordinate(4.69875, 51.83797),
      new Coordinate(4.5382, 51.91703),
      new Coordinate(4.09365, 51.98509),
    }));
  }
}
