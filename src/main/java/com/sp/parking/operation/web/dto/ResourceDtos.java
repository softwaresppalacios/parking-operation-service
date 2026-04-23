package com.sp.parking.operation.web.dto;

import com.sp.parking.operation.domain.model.*;

public final class ResourceDtos {
  private ResourceDtos() {}
  public record ResourceResponse(String code, ResourceKind kind, VehicleType vehicleType, boolean occupied, boolean reserved, String assignedPlate) {}
  public record ResourceBoardResponse(java.util.List<ResourceResponse> carSpaces, java.util.List<ResourceResponse> motorcycleSpaces, java.util.List<ResourceResponse> keySlots, java.util.List<ResourceResponse> helmetLockers) {}
}

