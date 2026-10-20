package com.sp.parking.operation.web.dto;

import com.sp.parking.operation.domain.model.VehicleType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public final class MonthlySubscriptionDtos {
  private MonthlySubscriptionDtos() {}

  public record CreateMonthlySubscriptionRequest(
    @NotBlank String customerName,
    String customerDocument,
    String customerPhone,
    @NotBlank String plate,
    @NotNull VehicleType vehicleType,
    @NotNull @DecimalMin("0.0") BigDecimal amount,
    String paymentMethod,
    @Size(max = 80, message = "La referencia del pago es demasiado larga") String onlineReference,
    @NotNull LocalDate startsAt,
    @Min(1) Integer months
  ) {}

  public record MonthlySubscriptionResponse(
    Long id,
    UUID uuid,
    String customerName,
    String customerDocument,
    String customerPhone,
    String plate,
    VehicleType vehicleType,
    BigDecimal amount,
    String paymentMethod,
    LocalDate startsAt,
    LocalDate expiresAt,
    Instant paidAt,
    boolean active
  ) {}
}
