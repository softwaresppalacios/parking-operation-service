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

  /** Datos del cliente y del vehiculo. El valor mensual aplica a las proximas renovaciones; no cambia lo ya cobrado. */
  public record UpdateMonthlySubscriptionRequest(
    @NotBlank(message = "Ingresa el nombre del cliente") @Size(max = 120) String customerName,
    @Size(max = 20, message = "La cédula es demasiado larga") String customerDocument,
    @Size(max = 20, message = "El celular es demasiado largo") String customerPhone,
    @NotBlank(message = "Ingresa la placa") String plate,
    @NotNull(message = "Selecciona el tipo de vehículo") VehicleType vehicleType,
    @NotNull(message = "Ingresa el valor mensual") @DecimalMin(value = "0.0", message = "El valor mensual no puede ser negativo") BigDecimal amount
  ) {}

  /** Extiende la vigencia y registra el cobro. amount vacio = valor mensual actual por cada mes. */
  public record RenewMonthlySubscriptionRequest(
    @NotNull(message = "Indica cuántos meses renovar") @Min(value = 1, message = "Renueva al menos un mes") @Max(value = 24, message = "Máximo 24 meses por renovación") Integer months,
    @DecimalMin(value = "0.0", message = "El valor no puede ser negativo") BigDecimal amount,
    String paymentMethod,
    @Size(max = 80, message = "La referencia del pago es demasiado larga") String onlineReference
  ) {}

  public record CancelMonthlySubscriptionRequest(
    @Size(max = 200, message = "El motivo es demasiado largo") String reason
  ) {}

  /** status: ACTIVE (vigente), EXPIRED (vencida, se puede renovar) o CANCELLED. */
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
    boolean active,
    String status,
    Instant renewedAt,
    Instant cancelledAt,
    String cancelReason,
    /** Recordatorio de vencimiento del periodo actual: cuando se envio, o el error del ultimo intento. */
    Instant reminderSentAt,
    String reminderError
  ) {}
}
