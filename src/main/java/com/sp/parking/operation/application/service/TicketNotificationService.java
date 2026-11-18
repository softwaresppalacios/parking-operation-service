package com.sp.parking.operation.application.service;

import com.sp.parking.operation.application.mapper.TicketMapper;
import com.sp.parking.operation.domain.model.ParkingTicket;
import com.sp.parking.operation.domain.model.TicketStatus;
import com.sp.parking.operation.domain.repository.ParkingTicketRepository;
import com.sp.parking.operation.infraestructure.client.WhatsappClient;
import com.sp.parking.operation.web.dto.TicketDtos.WhatsappChannel;
import com.sp.parking.operation.web.dto.TicketDtos.WhatsappStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * Envia el ticket de entrada al conductor con la plantilla de WhatsApp "ticket_sp_parqueaderos".
 * Variables en orden: ID ticket, Parqueadero, Nombre, Cedula, Lugar, Placa, Fecha y hora.
 */
@Service
@RequiredArgsConstructor
public class TicketNotificationService {
  private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy h:mma", Locale.US).withZone(ZoneId.of("America/Bogota"));

  private final ParkingTicketRepository ticketRepository;
  private final TenantConfigurationClient tenantConfigurationClient;
  private final WhatsappClient whatsappClient;

  /** Se llama despues de guardar la entrada: un fallo aqui no deshace el registro. */
  public WhatsappStatus sendEntryTicket(UUID tenantId, UUID ticketUuid) {
    ParkingTicket ticket = ticketRepository.findByTenantIdAndUuid(tenantId, ticketUuid).orElse(null);
    if (ticket == null) return new WhatsappStatus(false, "Ticket no encontrado");
    if (ticket.getDriverPhone() == null || ticket.getDriverPhone().isBlank()) {
      return new WhatsappStatus(false, "El ticket no tiene celular del conductor");
    }
    String parkingName;
    try {
      parkingName = tenantConfigurationClient.findBranchResources(tenantId).name();
    } catch (RuntimeException ex) {
      parkingName = null;
    }
    // Arrays.asList admite nulos (nombre o cedula vacios); el cliente los envia como "—".
    WhatsappClient.Result result = whatsappClient.sendTemplate(ticket.getDriverPhone(), Arrays.asList(
      TicketMapper.code(ticket.getUuid()),
      parkingName == null ? "SP Parqueaderos" : parkingName,
      ticket.getDriverName(),
      ticket.getDriverDocument(),
      ticket.getSpaceCode() == null || ticket.getSpaceCode().isBlank() ? "Sin asignar" : ticket.getSpaceCode(),
      displayPlate(ticket.getPlate()),
      WHEN.format(ticket.getCheckedInAt())
    ));
    return new WhatsappStatus(result.sent(), result.error());
  }

  /** Si el envio esta activo; con WHATSAPP_ENABLED=false la entrada no intenta enviar nada. */
  public boolean isEnabled() {
    return whatsappClient.isEnabled();
  }

  public WhatsappChannel channel() {
    return new WhatsappChannel(whatsappClient.isEnabled(), whatsappClient.isConfigured());
  }

  /** Reenvio manual desde la pantalla de entrada; solo para vehiculos que siguen adentro. */
  @Transactional(readOnly = true)
  public WhatsappStatus resend(UUID tenantId, UUID ticketUuid) {
    if (!whatsappClient.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, WhatsappClient.PAUSED_MESSAGE);
    }
    ParkingTicket ticket = ticketRepository.findByTenantIdAndUuid(tenantId, ticketUuid)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
    if (ticket.getStatus() != TicketStatus.ACTIVE) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Este vehículo ya registró su salida");
    }
    return sendEntryTicket(tenantId, ticketUuid);
  }

  /** CQX596 -> CQX 596, como se imprime en el ticket. */
  private static String displayPlate(String plate) {
    if (plate == null) return "";
    return plate.length() > 3 ? plate.substring(0, 3) + " " + plate.substring(3) : plate;
  }
}
