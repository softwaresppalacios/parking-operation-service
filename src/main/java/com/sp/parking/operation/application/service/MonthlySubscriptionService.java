package com.sp.parking.operation.application.service;

import com.sp.parking.operation.domain.model.MonthlySubscription;
import com.sp.parking.operation.domain.model.PaymentMethods;
import com.sp.parking.operation.domain.repository.MonthlySubscriptionRepository;
import com.sp.parking.operation.infraestructure.client.PaymentClient;
import com.sp.parking.operation.web.dto.MonthlySubscriptionDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MonthlySubscriptionService {
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final MonthlySubscriptionRepository repository;
  private final PaymentClient paymentClient;

  @Transactional(readOnly = true)
  public List<MonthlySubscriptionResponse> findAll(UUID tenantId) {
    return repository.findByTenantIdOrderByPaidAtDesc(tenantId).stream().map(this::toResponse).toList();
  }

  /** Crea la mensualidad y registra su cobro en caja en la misma operacion; si el pago falla, no se crea. */
  @Transactional
  public MonthlySubscriptionResponse create(UUID tenantId, CreateMonthlySubscriptionRequest request) {
    String method = PaymentMethods.require(request.paymentMethod() == null || request.paymentMethod().isBlank() ? PaymentMethods.CASH : request.paymentMethod());
    MonthlySubscription subscription = MonthlySubscription.builder()
      .tenantId(tenantId)
      .customerName(request.customerName().trim())
      .customerDocument(request.customerDocument())
      .customerPhone(request.customerPhone())
      .plate(normalizePlate(request.plate()))
      .vehicleType(request.vehicleType())
      .amount(request.amount())
      .paymentMethod(method)
      .startsAt(request.startsAt())
      .expiresAt(request.startsAt().plusMonths(request.months() == null ? 1 : request.months()))
      .build();
    MonthlySubscription saved = repository.save(subscription);
    if (saved.getAmount().signum() > 0) {
      paymentClient.register(tenantId, new PaymentClient.PaymentRequest(
        "MONTHLY", saved.getUuid(), saved.getAmount(), method, request.onlineReference(),
        saved.getPlate(), saved.getVehicleType() == null ? null : saved.getVehicleType().name(), null, null,
        "Mensualidad " + DAY.format(saved.getStartsAt()) + " al " + DAY.format(saved.getExpiresAt())
      ));
    }
    return toResponse(saved);
  }

  private MonthlySubscriptionResponse toResponse(MonthlySubscription subscription) {
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
      subscription.isActive() && !subscription.getExpiresAt().isBefore(java.time.LocalDate.now())
    );
  }

  private String normalizePlate(String plate) {
    String normalized = plate == null ? "" : plate.toUpperCase().replaceAll("[^A-Z0-9]", "");
    return normalized.substring(0, Math.min(10, normalized.length()));
  }
}
