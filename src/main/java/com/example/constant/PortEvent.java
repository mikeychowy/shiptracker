package com.example.constant;

import io.micronaut.core.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

public enum PortEvent {
  ENTRY,
  EXIT;

  @Nullable public PortEvent fromString(String event) {
    var safeString = StringUtils.toRootUpperCase(StringUtils.stripToEmpty(event));
    if (StringUtils.isBlank(safeString)) {
      return null;
    }
    try {
      return PortEvent.valueOf(safeString);
    } catch (Exception e) {
      return null;
    }
  }
}
