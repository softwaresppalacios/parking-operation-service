package com.sp.parking.operation.domain.repository;

import com.sp.parking.operation.domain.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingResourceRepository extends JpaRepository<ParkingResource, Long> {
  List<ParkingResource> findByTenantIdAndKindOrderByCode(UUID tenantId, ResourceKind kind);
  Optional<ParkingResource> findByTenantIdAndKindAndCode(UUID tenantId, ResourceKind kind, String code);
  Optional<ParkingResource> findFirstByTenantIdAndKindAndVehicleTypeAndOccupiedFalseAndReservedFalseOrderByCode(UUID tenantId, ResourceKind kind, VehicleType vehicleType);
  Optional<ParkingResource> findFirstByTenantIdAndKindAndOccupiedFalseAndReservedFalseOrderByCode(UUID tenantId, ResourceKind kind);
  long countByTenantIdAndKindAndOccupiedTrue(UUID tenantId, ResourceKind kind);
  long countByTenantIdAndKindAndOccupiedFalse(UUID tenantId, ResourceKind kind);
}

