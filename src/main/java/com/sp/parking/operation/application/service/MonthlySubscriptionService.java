package com.sp.parking.operation.application.service;

import com.sp.parking.operation.domain.model.MonthlySubscription;
import com.sp.parking.operation.domain.model.PaymentMethods;
import com.sp.parking.operation.domain.repository.MonthlySubscriptionRepository;
import com.sp.parking.operation.infraestructure.client.PaymentClient;
import com.sp.parking.operation.web.dto.MonthlySubscriptionDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mensualidades: venta, edicion de datos, renovacion (extiende la misma mensualidad) y cancelacion.
 * Cada venta o renovacion registra su cobro en caja dentro de la misma transaccion: si el pago falla, no se guarda.
 */
@Service
@RequiredArgsConstructor
public class MonthlySubscriptionService {
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

  private final MonthlySubscriptionRepository repository;
  private final PaymentClient paymentClient;

  @Transactional(readOnly = true)
  public List<MonthlySubscriptionResponse> findAll(UUID tenantId) {
    return repository.findByTenantIdOrderByPaidAtDesc(tenantId).stream().map(this::toResponse).toList();
  }

  /** Crea la mensualidad y registra su cobro en caja en la misma operacion; si el pago falla, no se crea. */
  @Transactional
  public MonthlySubscriptionResponse create(UUID tenantId, CreateMonthlySubscriptionRequest request) {
    String method = paymentMethod(request.paymentMethod());
    String plate = normalizePlate(request.plate());
    ensureNoOtherActive(tenantId, plate, null);
    MonthlySubscription subscription = MonthlySubscription.builder()
      .tenantId(tenantId)
      .customerName(request.customerName().trim())
      .customerDocument(blankToNull(request.customerDocument()))
      .customerPhone(blankToNull(request.customerPhone()))
      .plate(plate)
      .vehicleType(request.vehicleType())
      .amount(request.amount())
      .paymentMethod(method)
      .startsAt(request.startsAt())
      .expiresAt(request.startsAt().plusMonths(request.months() == null ? 1 : request.months()))
      .build();
    MonthlySubscription saved = repository.save(subscription);
    registerPayment(tenantId, saved, saved.getAmount(), method, request.onlineReference(),
      "Mensualidad " + DAY.format(saved.getStartsAt()) + " al " + DAY.format(saved.getExpiresAt()));
    return toResponse(saved);
  }

  /** Corrige datos del cliente o del vehiculo. No toca vigencia ni lo ya cobrado. */
  @Transactional
  public MonthlySubscriptionResponse update(UUID tenantId, UUID uuid, UpdateMonthlySubscriptionRequest request) {
    MonthlySubscription subscription = find(tenantId, uuid);
    if (!subscription.isActive()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede editar una mensualidad cancelada");
    }
    String plate = normalizePlate(request.plate());
    if (plate.length() < 5) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa una placa válida");
    if (!plate.equals(subscription.getPlate())) ensureNoOtherActive(tenantId, plate, subscription.getUuid());
    subscription.setCustomerName(request.customerName().trim());
    subscription.setCustomerDocument(blankToNull(request.customerDocument()));
    subscription.setCustomerPhone(blankToNull(request.customerPhone()));
    subscription.setPlate(plate);
    subscription.setVehicleType(request.vehicleType());
    subscription.setAmount(request.amount());
    subscription.setUpdatedAt(Instant.now());
    return toResponse(subscription);
  }

  /**
   * Extiende la misma mensualidad y cobra la renovacion. Si sigue vigente se suma desde su vencimiento
   * (el cliente no pierde dias); si ya vencio, el nuevo periodo empieza hoy.
   */
  @Transactional
  public MonthlySubscriptionResponse renew(UUID tenantId, UUID uuid, RenewMonthlySubscriptionRequest request) {
    MonthlySubscription subscription = find(tenantId, uuid);
    if (!subscription.isActive()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta mensualidad está cancelada; registra una nueva");
    }
    int months = request.months();
    LocalDate today = LocalDate.now(BOGOTA);
    LocalDate periodStart;
    if (subscription.getExpiresAt().isBefore(today)) {
      periodStart = today;
      subscription.setStartsAt(today);
    } else {
      periodStart = subscription.getExpiresAt();
    }
    LocalDate newExpiry = periodStart.plusMonths(months);
    BigDecimal amount = request.amount() != null
      ? request.amount()
      : subscription.getAmount().multiply(BigDecimal.valueOf(months));
    String method = paymentMethod(request.paymentMethod());
    subscription.setExpiresAt(newExpiry);
    subscription.setPaymentMethod(method);
    subscription.setRenewedAt(Instant.now());
    subscription.setPaidAt(Instant.now());
    subscription.setUpdatedAt(Instant.now());
    registerPayment(tenantId, subscription, amount, method, request.onlineReference(),
      "Renovación mensualidad " + DAY.format(periodStart) + " al " + DAY.format(newExpiry));
    return toResponse(subscription);
  }

  /** Deja la mensualidad sin efecto: la placa ya no entra como mensualidad. No hace devoluciones de dinero. */
  @Transactional
  public MonthlySubscriptionResponse cancel(UUID tenantId, UUID uuid, CancelMonthlySubscriptionRequest request) {
    MonthlySubscription subscription = find(tenantId, uuid);
    if (!subscription.isActive()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta mensualidad ya estaba cancelada");
    }
    subscription.setActive(false);
    subscription.setCancelledAt(Instant.now());
    subscription.setCancelReason(request == null ? null : blankToNull(request.reason()));
    subscription.setUpdatedAt(Instant.now());
    return toResponse(subscription);
  }

  /** Mensualidad vigente hoy para la placa (no cancelada y sin vencer). */
  @Transactional(readOnly = true)
  public Optional<MonthlySubscription> currentFor(UUID tenantId, String plate) {
    return repository.findFirstByTenantIdAndPlateIgnoreCaseAndActiveTrueAndExpiresAtGreaterThanEqualOrderByExpiresAtDesc(
      tenantId, normalizePlate(plate), LocalDate.now(BOGOTA));
  }

  private void ensureNoOtherActive(UUID tenantId, String plate, UUID exceptUuid) {
    currentFor(tenantId, plate)
      .filter(existing -> !existing.getUuid().equals(exceptUuid))
      .ifPresent(existing -> {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
          "La placa " + plate + " ya tiene una mensualidad vigente hasta el " + DAY.format(existing.getExpiresAt()) + "; renuévala en lugar de crear otra");
      });
  }

  private void registerPayment(UUID tenantId, MonthlySubscription subscription, BigDecimal amount, String method, String onlineReference, String description) {
    if (amount == null || amount.signum() <= 0) return;
    paymentClient.register(tenantId, new PaymentClient.PaymentRequest(
      "MONTHLY", subscription.getUuid(), amount, method, onlineReference,
      subscription.getPlate(), subscription.getVehicleType() == null ? null : subscription.getVehicleType().name(), null, null,
      description
    ));
  }

  private MonthlySubscription find(UUID tenantId, UUID uuid) {
    return repository.findByTenantIdAndUuid(tenantId, uuid)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensualidad no encontrada"));
  }

  private String status(MonthlySubscription subscription) {
    if (!subscription.isActive()) return "CANCELLED";
    return subscription.getExpiresAt().isBefore(LocalDate.now(BOGOTA)) ? "EXPIRED" : "ACTIVE";
  }

  private MonthlySubscriptionResponse toResponse(MonthlySubscription subscription) {
    String status = status(subscription);
    return new MonthlySubscriptionResponse(
      subscription.getId(),
      subscription.getUuid(),
      subscription.getCustomerName(),
      subscription.getCustomerDocument(),
      subscription.getCustomerPhone(),
      subscription.getPlate(),
      subscription.getVehicleType(),
      subscription.getAmount(),
      subscription.getPaymentMethod(),
      subscription.getStartsAt(),
      subscription.getExpiresAt(),
      subscription.getPaidAt(),
      "ACTIVE".equals(status),
      status,
      subscription.getRenewedAt(),
      subscription.getCancelledAt(),
      subscription.getCancelReason()
    );
  }

  private String paymentMethod(String method) {
    return PaymentMethods.require(method == null || method.isBlank() ? PaymentMethods.CASH : method);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String normalizePlate(String plate) {
    String normalized = plate == null ? "" : plate.toUpperCase().replaceAll("[^A-Z0-9]", "");
    return normalized.substring(0, Math.min(10, normalized.length()));
  }
}
