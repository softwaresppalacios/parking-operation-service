package com.sp.parking.operation.application.mapper;

import com.sp.parking.operation.domain.model.ParkingTicket;
import com.sp.parking.operation.web.dto.TicketDtos.TicketResponse;
import org.springframework.stereotype.Component;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class TicketMapper {
  public TicketResponse toResponse(ParkingTicket ticket) {
    return new TicketResponse(
      ticket.getUuid(),
      ticket.getPlate(),
      ticket.getVehicleType(),
      ticket.getSpaceCode(),
      ticket.getKeySlotCode(),
      ticket.getHelmetLockerCode(),
      Stream.of(ticket.getHelmetLockerCode(), ticket.getSecondHelmetLockerCode()).filter(Objects::nonNull).toList(),
      ticket.isMonthlySubscription(),
      ticket.getCheckedInAt(),
      ticket.getCheckedOutAt(),
      ticket.getStatus() == null ? "ACTIVE" : ticket.getStatus().name(),
      ticket.getAmountDue(),
      ticket.getPaymentMethod(),
      code(ticket.getUuid()),
      ticket.getDriverName(),
      ticket.getDriverDocument()
    );
  }

  /** Mismo formato que muestra el frontend en la vista previa del ticket. */
  public static String code(UUID uuid) {
    return "SPP-" + uuid.toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
  }
}
