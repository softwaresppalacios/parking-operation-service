package com.sp.parking.operation.web.controller;

import com.sp.parking.operation.application.service.ParkingTicketService;
import com.sp.parking.operation.web.dto.TicketDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {
  private final ParkingTicketService ticketService;

  @PostMapping("/check-in")
  public TicketResponse checkIn(@RequestHeader("X-Tenant-Id") UUID tenantId, @Valid @RequestBody CheckInRequest request) {
    return ticketService.checkIn(tenantId, request);
  }

  @PostMapping("/{ticketUuid}/check-out")
  public TicketResponse checkOut(@RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable("ticketUuid") UUID ticketUuid, @RequestBody CheckOutRequest request) {
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
}
