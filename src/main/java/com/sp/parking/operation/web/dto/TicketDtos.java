package com.sp.parking.operation.web.dto;

import com.sp.parking.operation.domain.model.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TicketDtos {
  private TicketDtos() {}
  public record CheckInRequest(
    @NotBlank String plate,
    VehicleType vehicleType,
    String driverPhone,
    @Size(max = 120, message = "El nombre es demasiado largo") String driverName,
    @Size(max = 20, message = "La cédula es demasiado larga") String driverDocument,
    String spaceCode,
    String keySlotCode,
    String helmetLockerCode,
    /** Casilleros de casco (uno por casco): hasta 2 para motos. Se suma a helmetLockerCode si tambien llega. */
    @Size(max = 2, message = "Una moto puede guardar máximo 2 cascos") List<String> helmetLockerCodes,
    boolean monthlySubscription,
    /** Enviar el ticket al conductor con la plantilla de WhatsApp; por defecto si. */
    Boolean sendWhatsapp
  ) {}

  /** Resultado del envio del ticket por WhatsApp: el registro de la entrada no depende de el. */
  public record WhatsappStatus(boolean sent, String error) {}
  /** enabled: no esta en pausa; configured: hay numero y token de Meta. */
  public record WhatsappChannel(boolean enabled, boolean configured) {}

  public record CheckInResponse(TicketResponse ticket, WhatsappStatus whatsapp) {}
  /** El valor lo calcula el servidor con pricing-service; aqui solo llega como se pago. */
  public record CheckOutRequest(
    String paymentMethod,
    @Size(max = 80, message = "La referencia del pago es demasiado larga") String onlineReference
  ) {}
  public record TicketResponse(
    UUID uuid,
    String plate,
    VehicleType vehicleType,
    String spaceCode,
    String keySlotCode,
    String helmetLockerCode,
    /** Todos los casilleros de casco del ticket (0, 1 o 2). */
    List<String> helmetLockerCodes,
    boolean monthlySubscription,
    Instant checkedInAt,
    Instant checkedOutAt,
    String status,
    BigDecimal amountDue,
    String paymentMethod,
    /** Codigo corto impreso y enviado al conductor (SPP-XXXXXX). */
    String code,
    String driverName,
    String driverDocument
  ) {}
}
