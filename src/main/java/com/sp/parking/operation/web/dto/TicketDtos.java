package com.sp.parking.operation.web.dto;

import com.sp.parking.operation.domain.model.VehicleType;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TicketDtos {
  private TicketDtos() {}
  public record CheckInRequest(
    @NotBlank String plate,
    VehicleType vehicleType,
    String driverPhone,
    String spaceCode,
    String keySlotCode,
    String helmetLockerCode,
    boolean monthlySubscription
  ) {}
  public record CheckOutRequest(BigDecimal amountDue) {}
  public record TicketResponse(
    UUID uuid,
    String plate,
    VehicleType vehicleType,
    String spaceCode,
    String keySlotCode,
    String helmetLockerCode,
    boolean monthlySubscription,
    Instant checkedInAt,
    Instant checkedOutAt,
    String status,
    BigDecimal amountDue
  ) {}
}
