package com.sp.parking.operation.domain.repository;

import com.sp.parking.operation.domain.model.MonthlySubscription;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlySubscriptionRepository extends JpaRepository<MonthlySubscription, Long> {
  List<MonthlySubscription> findByTenantIdOrderByPaidAtDesc(UUID tenantId);
  Optional<MonthlySubscription> findFirstByTenantIdAndPlateIgnoreCaseAndActiveTrueOrderByExpiresAtDesc(UUID tenantId, String plate);
  Optional<MonthlySubscription> findByTenantIdAndUuid(UUID tenantId, UUID uuid);
  /** Mensualidad activa (no cancelada) cuya vigencia cubre la fecha dada. */
  Optional<MonthlySubscription> findFirstByTenantIdAndPlateIgnoreCaseAndActiveTrueAndExpiresAtGreaterThanEqualOrderByExpiresAtDesc(UUID tenantId, String plate, LocalDate date);
}
