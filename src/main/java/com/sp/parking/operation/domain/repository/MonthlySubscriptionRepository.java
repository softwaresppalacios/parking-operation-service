package com.sp.parking.operation.domain.repository;

import com.sp.parking.operation.domain.model.MonthlySubscription;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlySubscriptionRepository extends JpaRepository<MonthlySubscription, Long> {
  List<MonthlySubscription> findByTenantIdOrderByPaidAtDesc(UUID tenantId);
  Optional<MonthlySubscription> findFirstByTenantIdAndPlateIgnoreCaseAndActiveTrueOrderByExpiresAtDesc(UUID tenantId, String plate);
}
