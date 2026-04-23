package com.sp.parking.operation.application.service;

import com.sp.parking.operation.application.mapper.TicketMapper;
import com.sp.parking.operation.domain.model.*;
import com.sp.parking.operation.domain.repository.*;
import com.sp.parking.operation.web.dto.TicketDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ParkingTicketService {
  private final ParkingTicketRepository ticketRepository;
  private final ParkingResourceRepository resourceRepository;
  private final ParkingResourceService resourceService;
  private final TicketMapper mapper;

  @Transactional
  public TicketResponse checkIn(UUID tenantId, CheckInRequest request) {
    VehicleType type = request.vehicleType() == null ? VehicleType.CAR : request.vehicleType();
    resourceService.ensureDefaults(tenantId);
    String normalizedPlate = normalizePlate(request.plate());
    ticketRepository.findFirstByTenantIdAndPlateIgnoreCaseAndStatus(tenantId, normalizedPlate, TicketStatus.ACTIVE)
      .ifPresent(t -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle already has an active ticket"); });

    ParkingResource space = reserveSpace(tenantId, type, request.spaceCode(), normalizedPlate);
    ParkingResource keySlot = null;
    ParkingResource helmetLocker = null;
    if (type.requiresKeySlot()) {
      keySlot = reserveRequiredResource(tenantId, ResourceKind.KEY_SLOT, request.keySlotCode(), normalizedPlate, "Key slot is required");
    } else if (request.helmetLockerCode() != null && !request.helmetLockerCode().isBlank()) {
      helmetLocker = reserveRequiredResource(tenantId, ResourceKind.HELMET_LOCKER, request.helmetLockerCode(), normalizedPlate, "Helmet locker is not available");
    }

    ParkingTicket ticket = ParkingTicket.builder()
      .tenantId(tenantId)
      .plate(normalizedPlate)
      .vehicleType(type)
      .driverPhone(request.driverPhone())
      .spaceCode(space.getCode())
      .keySlotCode(keySlot == null ? null : keySlot.getCode())
      .helmetLockerCode(helmetLocker == null ? null : helmetLocker.getCode())
      .monthlySubscription(request.monthlySubscription())
      .build();
    return mapper.toResponse(ticketRepository.save(ticket));
  }

  @Transactional
  public TicketResponse checkOut(UUID tenantId, UUID ticketUuid, CheckOutRequest request) {
    ParkingTicket ticket = ticketRepository.findByTenantIdAndUuid(tenantId, ticketUuid)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
    ticket.setCheckedOutAt(Instant.now());
    ticket.setAmountDue(request.amountDue());
    ticket.setStatus(TicketStatus.PAID);
    releaseResource(tenantId, ResourceKind.PARKING_SPACE, ticket.getSpaceCode());
    releaseResource(tenantId, ResourceKind.KEY_SLOT, ticket.getKeySlotCode());
    releaseResource(tenantId, ResourceKind.HELMET_LOCKER, ticket.getHelmetLockerCode());
    return mapper.toResponse(ticket);
  }

  @Transactional(readOnly = true)
  public TicketResponse findActiveByPlate(UUID tenantId, String plate) {
    return ticketRepository.findFirstByTenantIdAndPlateIgnoreCaseAndStatus(tenantId, normalizePlate(plate), TicketStatus.ACTIVE)
      .map(mapper::toResponse)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active vehicle not found"));
  }

  @Transactional(readOnly = true)
  public List<TicketResponse> active(UUID tenantId) {
    return ticketRepository.findByTenantIdAndStatusOrderByCheckedInAtDesc(tenantId, TicketStatus.ACTIVE).stream().map(mapper::toResponse).toList();
  }

  private ParkingResource reserveSpace(UUID tenantId, VehicleType type, String requestedCode, String plate) {
    VehicleType spaceType = type.usesMotorcycleSpace() ? VehicleType.MOTORCYCLE : VehicleType.CAR;
    ParkingResource resource = requestedCode == null || requestedCode.isBlank()
      ? resourceRepository.findFirstByTenantIdAndKindAndVehicleTypeAndOccupiedFalseAndReservedFalseOrderByCode(tenantId, ResourceKind.PARKING_SPACE, spaceType)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No parking spaces available for vehicle type"))
      : resourceRepository.findByTenantIdAndKindAndCode(tenantId, ResourceKind.PARKING_SPACE, requestedCode)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parking space not found"));
    if (resource.isOccupied() || resource.isReserved()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Parking space is not available");
    }
    if (resource.getVehicleType() != spaceType) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected space does not match vehicle type");
    }
    resource.setOccupied(true);
    resource.setAssignedPlate(plate);
    return resource;
  }

  private ParkingResource reserveRequiredResource(UUID tenantId, ResourceKind kind, String code, String plate, String message) {
    if (code == null || code.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    ParkingResource resource = resourceRepository.findByTenantIdAndKindAndCode(tenantId, kind, code)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, message));
    if (resource.isOccupied() || resource.isReserved()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
    resource.setOccupied(true);
    resource.setAssignedPlate(plate);
    return resource;
  }

  private void releaseResource(UUID tenantId, ResourceKind kind, String code) {
    if (code == null || code.isBlank()) return;
    resourceRepository.findByTenantIdAndKindAndCode(tenantId, kind, code).ifPresent(resource -> {
      resource.setOccupied(false);
      resource.setAssignedPlate(null);
    });
  }

  private String normalizePlate(String plate) {
    return plate == null ? "" : plate.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
  }
}
