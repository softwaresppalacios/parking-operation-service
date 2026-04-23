package com.sp.parking.operation.application.mapper;

import com.sp.parking.operation.domain.model.ParkingTicket;
import com.sp.parking.operation.web.dto.TicketDtos.TicketResponse;
import org.springframework.stereotype.Component;

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
      ticket.isMonthlySubscription(),
      ticket.getCheckedInAt(),
      ticket.getCheckedOutAt(),
      ticket.getStatus() == null ? "ACTIVE" : ticket.getStatus().name(),
      ticket.getAmountDue()
    );
  }
}
