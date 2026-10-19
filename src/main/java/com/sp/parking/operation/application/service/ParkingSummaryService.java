package com.sp.parking.operation.application.service;

import com.sp.parking.operation.domain.model.TicketStatus;
import com.sp.parking.operation.domain.model.ResourceKind;
import com.sp.parking.operation.domain.model.VehicleType;
import com.sp.parking.operation.domain.repository.*;
import com.sp.parking.operation.web.dto.SummaryDtos.ParkingSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ParkingSummaryService {
  private final ParkingTicketRepository ticketRepository;
  private final ParkingResourceRepository resourceRepository;
  private final ParkingResourceService resourceService;
  private final TenantConfigurationClient tenantConfigurationClient;

  @Transactional
  public ParkingSummaryResponse current(UUID tenantId) {
    TenantConfigurationClient.BranchResources config = tenantConfigurationClient.findBranchResources(tenantId);
    if (config.manageResources()) {
      resourceService.ensureDefaults(tenantId);
    }
    long carOccupied = countOccupied(tenantId, config.manageResources(), VehicleType.CAR);
    long carAvailable = countAvailable(tenantId, config.manageResources(), VehicleType.CAR);
    long motorcycleOccupied = countOccupied(tenantId, config.manageResources(), VehicleType.MOTORCYCLE);
    long motorcycleAvailable = countAvailable(tenantId, config.manageResources(), VehicleType.MOTORCYCLE);
    return new ParkingSummaryResponse(
      ticketRepository.countByTenantIdAndStatus(tenantId, TicketStatus.ACTIVE),
      carOccupied + motorcycleOccupied,
      carAvailable + motorcycleAvailable,
      carOccupied,
      carAvailable,
      motorcycleOccupied,
      motorcycleAvailable
    );
  }

  private long countOccupied(UUID tenantId, boolean manageResources, VehicleType vehicleType) {
    return manageResources
      ? resourceRepository.countByTenantIdAndKindAndVehicleTypeAndOccupiedTrue(tenantId, ResourceKind.PARKING_SPACE, vehicleType)
      : 0;
  }

  private long countAvailable(UUID tenantId, boolean manageResources, VehicleType vehicleType) {
    return manageResources
      ? resourceRepository.countByTenantIdAndKindAndVehicleTypeAndOccupiedFalseAndReservedFalse(tenantId, ResourceKind.PARKING_SPACE, vehicleType)
      : 0;
  }
}
