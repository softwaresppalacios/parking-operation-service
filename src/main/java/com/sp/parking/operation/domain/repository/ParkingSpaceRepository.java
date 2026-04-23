package com.sp.parking.operation.domain.repository;

import com.sp.parking.operation.domain.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingSpaceRepository extends JpaRepository<ParkingSpace, Long> {
  Optional<ParkingSpace> findFirstByTenantIdAndVehicleTypeAndOccupiedFalseOrderByCode(UUID tenantId, VehicleType type);
  Optional<ParkingSpace> findByTenantIdAndCode(UUID tenantId, String code);
  long countByTenantIdAndOccupiedTrue(UUID tenantId);
  long countByTenantIdAndOccupiedFalse(UUID tenantId);
}

