package com.sp.parking.operation.web.controller;

import com.sp.parking.operation.application.service.ParkingTicketService;
import com.sp.parking.operation.application.service.TicketNotificationService;
import com.sp.parking.operation.web.dto.TicketDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import com.sp.parking.operation.infraestructure.client.AuditClient;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {
  private final ParkingTicketService ticketService;
  private final TicketNotificationService notificationService;
  private final AuditClient auditClient;

  /** Registra la entrada y luego (ya guardada) envia el ticket por WhatsApp si se pidio. */
  @PostMapping("/check-in")
  public CheckInResponse checkIn(@RequestHeader("X-Tenant-Id") UUID tenantId, @Valid @RequestBody CheckInRequest request) {
    TicketResponse ticket = ticketService.checkIn(tenantId, request);
    auditClient.record(tenantId, "CHECK_IN", "TICKET", ticket.code() + " · " + ticket.plate(), String.join(" · ",
      "Placa " + ticket.plate(),
      AuditClient.vehicle(ticket.vehicleType()),
      "Espacio " + (ticket.spaceCode() == null ? "sin asignar" : ticket.spaceCode()),
      ticket.monthlySubscription() ? "Mensualidad" : "Cobro por tiempo"));
    if (Boolean.FALSE.equals(request.sendWhatsapp()) || !notificationService.isEnabled()) return new CheckInResponse(ticket, null);
    WhatsappStatus whatsapp;
    try {
      whatsapp = notificationService.sendEntryTicket(tenantId, ticket.uuid());
    } catch (RuntimeException ex) {
      // La entrada ya quedo guardada: un error del envio no puede convertirse en un error del registro.
      log.error("No se pudo enviar el ticket {} por WhatsApp", ticket.code(), ex);
      whatsapp = new WhatsappStatus(false, "No se pudo enviar el ticket por WhatsApp");
    }
    return new CheckInResponse(ticket, whatsapp);
  }

  /** Estado del canal de WhatsApp, para que la pantalla de entrada sepa si esta en pausa. */
  @GetMapping("/whatsapp/status")
  public WhatsappChannel whatsappStatus() {
    return notificationService.channel();
  }

  @PostMapping("/{ticketUuid}/whatsapp")
  public WhatsappStatus resendWhatsapp(@RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable("ticketUuid") UUID ticketUuid) {
    return notificationService.resend(tenantId, ticketUuid);
  }

  @PostMapping("/{ticketUuid}/check-out")
  public TicketResponse checkOut(@RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable("ticketUuid") UUID ticketUuid, @Valid @RequestBody CheckOutRequest request) {
    TicketResponse ticket = ticketService.checkOut(tenantId, ticketUuid, request);
    String charge = ticket.monthlySubscription()
      ? "Mensualidad, sin cobro"
      : "Cobro " + AuditClient.money(ticket.amountDue()) + (ticket.paymentMethod() == null ? "" : " (" + ticket.paymentMethod() + ")");
    auditClient.record(tenantId, "CHECK_OUT", "TICKET", ticket.code() + " · " + ticket.plate(), "Placa " + ticket.plate() + " · " + charge);
    return ticket;
  }

  @GetMapping("/by-plate/{plate}")
  public TicketResponse byPlate(@RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable("plate") String plate) {
    return ticketService.findActiveByPlate(tenantId, plate);
  }

  @GetMapping("/active")
  public List<TicketResponse> active(@RequestHeader("X-Tenant-Id") UUID tenantId) {
    return ticketService.active(tenantId);
  }

  @GetMapping("/recent")
  public List<TicketResponse> recent(@RequestHeader("X-Tenant-Id") UUID tenantId) {
    return ticketService.recent(tenantId);
  }

  /** Historial de tickets por fecha de entrada (maximo un año). Por defecto el mes en curso. */
  @GetMapping("/history")
  public List<TicketResponse> history(
    @RequestHeader("X-Tenant-Id") UUID tenantId,
    @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
    @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    // Abierto a todos los roles: el cajero tambien consulta tickets. Los reportes de ingresos siguen restringidos.
    LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));
    return ticketService.history(tenantId, from == null ? today.withDayOfMonth(1) : from, to == null ? today : to);
  }
}
