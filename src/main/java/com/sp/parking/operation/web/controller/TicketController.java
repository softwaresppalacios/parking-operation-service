package com.sp.parking.operation.web.controller;

import com.sp.parking.operation.application.service.ParkingTicketService;
import com.sp.parking.operation.application.service.TicketNotificationService;
import com.sp.parking.operation.web.dto.TicketDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {
  private final ParkingTicketService ticketService;
  private final TicketNotificationService notificationService;

  /** Registra la entrada y luego (ya guardada) envia el ticket por WhatsApp si se pidio. */
  @PostMapping("/check-in")
  public CheckInResponse checkIn(@RequestHeader("X-Tenant-Id") UUID tenantId, @Valid @RequestBody CheckInRequest request) {
    TicketResponse ticket = ticketService.checkIn(tenantId, request);
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
    return ticketService.checkOut(tenantId, ticketUuid, request);
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
}
