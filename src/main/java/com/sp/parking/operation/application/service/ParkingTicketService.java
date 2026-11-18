package com.sp.parking.operation.application.service;

import com.sp.parking.operation.application.mapper.TicketMapper;
import com.sp.parking.operation.domain.model.*;
import com.sp.parking.operation.domain.repository.*;
import com.sp.parking.operation.infraestructure.client.PaymentClient;
import com.sp.parking.operation.infraestructure.client.PricingClient;
import com.sp.parking.operation.web.dto.TicketDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ParkingTicketService {
  private final ParkingTicketRepository ticketRepository;
  private final ParkingResourceRepository resourceRepository;
  private final ParkingResourceService resourceService;
  private final TenantConfigurationClient tenantConfigurationClient;
  private final PricingClient pricingClient;
  private final PaymentClient paymentClient;
  private final TicketMapper mapper;
  private final MonthlySubscriptionRepository monthlyRepository;

  @Transactional
  public TicketResponse checkIn(UUID tenantId, CheckInRequest request) {
    VehicleType type = request.vehicleType() == null ? VehicleType.CAR : request.vehicleType();
    resourceService.ensureDefaults(tenantId);
    TenantConfigurationClient.BranchResources config = tenantConfigurationClient.findBranchResources(tenantId);
    String normalizedPlate = normalizePlate(request.plate());
    ticketRepository.findFirstByTenantIdAndPlateIgnoreCaseAndStatus(tenantId, normalizedPlate, TicketStatus.ACTIVE)
      .ifPresent(t -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle already has an active ticket"); });
    if (request.monthlySubscription()) {
      // Una mensualidad cancelada o vencida no da derecho a entrar sin cobro.
      monthlyRepository.findFirstByTenantIdAndPlateIgnoreCaseAndActiveTrueAndExpiresAtGreaterThanEqualOrderByExpiresAtDesc(
          tenantId, normalizedPlate, LocalDate.now(ZoneId.of("America/Bogota")))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
          "La placa " + normalizedPlate + " no tiene una mensualidad vigente; regístrala con cobro por hora o renueva la mensualidad"));
    }

    ParkingResource space = config.manageResources() ? reserveSpace(tenantId, type, request.spaceCode(), normalizedPlate) : null;
    ParkingResource keySlot = null;
    List<String> helmetLockers = List.of();
    if (config.manageResources() && type.requiresKeySlot()) {
      keySlot = reserveOptionalResource(tenantId, ResourceKind.KEY_SLOT, request.keySlotCode(), normalizedPlate, "Key slot is not available");
    }
    if (config.manageResources() && (type == VehicleType.MOTORCYCLE || type == VehicleType.BICYCLE)) {
      helmetLockers = requestedHelmetLockers(request, type).stream()
        .map(code -> reserveOptionalResource(tenantId, ResourceKind.HELMET_LOCKER, code, normalizedPlate, "El casillero de casco " + code + " no está disponible"))
        .map(ParkingResource::getCode)
        .toList();
    }

    ParkingTicket ticket = ParkingTicket.builder()
      .tenantId(tenantId)
      .plate(normalizedPlate)
      .vehicleType(type)
      .driverPhone(request.driverPhone())
      .driverName(blankToNull(request.driverName()))
      .driverDocument(blankToNull(request.driverDocument()))
      .spaceCode(space == null ? normalizeOptionalCode(request.spaceCode()) : space.getCode())
      .keySlotCode(keySlot == null ? null : keySlot.getCode())
      .helmetLockerCode(helmetLockers.isEmpty() ? null : helmetLockers.get(0))
      .secondHelmetLockerCode(helmetLockers.size() > 1 ? helmetLockers.get(1) : null)
      .monthlySubscription(request.monthlySubscription())
      .build();
    return mapper.toResponse(ticketRepository.save(ticket));
  }

  /**
   * Registra la salida y su cobro como una sola operacion: el valor lo calcula pricing-service y el pago
   * entra a la caja de payment-cash-service. Si el pago falla, la salida se revierte.
   * Los vehiculos con mensualidad salen sin cobro.
   */
  @Transactional
  public TicketResponse checkOut(UUID tenantId, UUID ticketUuid, CheckOutRequest request) {
    ParkingTicket ticket = ticketRepository.findByTenantIdAndUuid(tenantId, ticketUuid)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
    if (ticket.getStatus() != TicketStatus.ACTIVE) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Este vehículo ya registró su salida");
    }
    Instant checkedOutAt = Instant.now();
    ticket.setCheckedOutAt(checkedOutAt);
    ticket.setStatus(TicketStatus.PAID);
    releaseResource(tenantId, ResourceKind.PARKING_SPACE, ticket.getSpaceCode());
    releaseResource(tenantId, ResourceKind.KEY_SLOT, ticket.getKeySlotCode());
    releaseResource(tenantId, ResourceKind.HELMET_LOCKER, ticket.getHelmetLockerCode());
    releaseResource(tenantId, ResourceKind.HELMET_LOCKER, ticket.getSecondHelmetLockerCode());

    if (ticket.isMonthlySubscription()) {
      ticket.setAmountDue(BigDecimal.ZERO);
      return mapper.toResponse(ticket);
    }
    BigDecimal total = pricingClient.amountDue(tenantId, ticket);
    // Descuento de monto exacto (no porcentaje), en pesos enteros. Nunca deja el cobro en negativo.
    BigDecimal discount = request.discountAmount() == null ? BigDecimal.ZERO : request.discountAmount().setScale(0, RoundingMode.HALF_UP);
    if (discount.compareTo(total) > 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "El descuento ($" + discount.toPlainString() + ") no puede ser mayor que el total a cobrar ($" + total.setScale(0, RoundingMode.HALF_UP).toPlainString() + ")");
    }
    BigDecimal amount = total.subtract(discount);
    ticket.setAmountDue(amount);
    if (discount.signum() > 0) {
      ticket.setDiscountAmount(discount);
      ticket.setDiscountReason(request.discountReason() == null || request.discountReason().isBlank() ? null : request.discountReason().trim());
    }
    if (amount.signum() > 0) {
      String method = PaymentMethods.require(request.paymentMethod());
      ticket.setPaymentMethod(method);
      String description = discount.signum() > 0
        ? "Tarifa $" + total.setScale(0, RoundingMode.HALF_UP).toPlainString() + " - descuento $" + discount.toPlainString()
          + (ticket.getDiscountReason() == null ? "" : " (" + ticket.getDiscountReason() + ")")
        : null;
      paymentClient.register(tenantId, new PaymentClient.PaymentRequest(
        "PARKING", ticket.getUuid(), amount, method, request.onlineReference(),
        ticket.getPlate(), ticket.getVehicleType().name(), ticket.getCheckedInAt(), checkedOutAt, description
      ));
    }
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

  @Transactional(readOnly = true)
  public List<TicketResponse> recent(UUID tenantId) {
    return ticketRepository.findTop25ByTenantIdOrderByCheckedInAtDesc(tenantId).stream().map(mapper::toResponse).toList();
  }

  /** Tickets con entrada en el rango de fechas (dias de Colombia), para reportes. Maximo un año. */
  @Transactional(readOnly = true)
  public List<TicketResponse> history(UUID tenantId, LocalDate from, LocalDate to) {
    if (to.isBefore(from)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha inicio debe ser anterior a la fecha fin");
    if (from.plusDays(366).isBefore(to)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rango máximo es de un año");
    ZoneId zone = ZoneId.of("America/Bogota");
    return ticketRepository.findByTenantIdAndCheckedInAtGreaterThanEqualAndCheckedInAtLessThanOrderByCheckedInAtDesc(
        tenantId, from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant())
      .stream().map(mapper::toResponse).toList();
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

  /** Casilleros de casco pedidos (uno por casco), sin repetidos: hasta 2 para motos y 1 para bicicletas. */
  private List<String> requestedHelmetLockers(CheckInRequest request, VehicleType type) {
    List<String> codes = Stream.concat(
        Stream.ofNullable(request.helmetLockerCode()),
        request.helmetLockerCodes() == null ? Stream.empty() : request.helmetLockerCodes().stream()
      )
      .filter(code -> code != null && !code.isBlank())
      .map(code -> code.trim().toUpperCase(Locale.ROOT))
      .distinct()
      .toList();
    int max = type == VehicleType.MOTORCYCLE ? 2 : 1;
    if (codes.size() > max) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, type == VehicleType.MOTORCYCLE
        ? "Una moto puede guardar máximo 2 cascos"
        : "Una bicicleta puede usar un solo casillero de casco");
    }
    return codes;
  }

  private ParkingResource reserveOptionalResource(UUID tenantId, ResourceKind kind, String code, String plate, String message) {
    if (code == null || code.isBlank()) {
      return null;
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

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String normalizeOptionalCode(String code) {
    if (code == null || code.isBlank()) return null;
    return code.trim().toUpperCase(Locale.ROOT);
  }
}
