package com.example.entity;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@Serdeable
@Introspected
@NoArgsConstructor
@AllArgsConstructor
public class LocationPart {
  @Nullable private List<Double> coordinates;

  @Nullable private String type;
}
