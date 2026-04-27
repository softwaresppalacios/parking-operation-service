package com.sp.parking.operation.application.service;

import com.sp.parking.operation.domain.model.MonthlySubscription;
import com.sp.parking.operation.domain.repository.MonthlySubscriptionRepository;
import com.sp.parking.operation.web.dto.MonthlySubscriptionDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MonthlySubscriptionService {
  private final MonthlySubscriptionRepository repository;

  @Transactional(readOnly = true)
  public List<MonthlySubscriptionResponse> findAll(UUID tenantId) {
    return repository.findByTenantIdOrderByPaidAtDesc(tenantId).stream().map(this::toResponse).toList();
  }

  @Transactional
  public MonthlySubscriptionResponse create(UUID tenantId, CreateMonthlySubscriptionRequest request) {
    MonthlySubscription subscription = MonthlySubscription.builder()
      .tenantId(tenantId)
      .customerName(request.customerName().trim())
      .customerDocument(request.customerDocument())
      .customerPhone(request.customerPhone())
      .plate(normalizePlate(request.plate()))
      .vehicleType(request.vehicleType())
      .amount(request.amount())
      .paymentMethod(request.paymentMethod() == null || request.paymentMethod().isBlank() ? "CASH" : request.paymentMethod())
      .startsAt(request.startsAt())
      .expiresAt(request.startsAt().plusMonths(request.months() == null ? 1 : request.months()))
      .build();
    return toResponse(repository.save(subscription));
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
