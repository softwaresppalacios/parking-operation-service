package com.sp.parking.operation.domain.model;

public enum VehicleType {
  CAR,
  MOTORCYCLE,
  HEAVY,
  BICYCLE;

  public boolean usesMotorcycleSpace() {
    return this == MOTORCYCLE || this == BICYCLE;
  }

  public boolean requiresKeySlot() {
    return this == CAR || this == HEAVY;
  }
}
