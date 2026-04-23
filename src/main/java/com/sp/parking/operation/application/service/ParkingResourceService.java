package com.sp.parking.operation.application.service;

import com.sp.parking.operation.domain.model.*;
import com.sp.parking.operation.domain.repository.ParkingResourceRepository;
import com.sp.parking.operation.web.dto.ResourceDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ParkingResourceService {
  private final ParkingResourceRepository resourceRepository;
  private final TenantConfigurationClient tenantConfigurationClient;

  @Transactional
  public ResourceBoardResponse board(UUID tenantId) {
    ensureDefaults(tenantId);
    List<ParkingResource> resources = resourceRepository.findAll().stream()
      .filter(resource -> resource.getTenantId().equals(tenantId))
      .toList();
    return new ResourceBoardResponse(
      resources.stream().filter(r -> r.getKind() == ResourceKind.PARKING_SPACE && r.getVehicleType() == VehicleType.CAR).map(this::toResponse).toList(),
      resources.stream().filter(r -> r.getKind() == ResourceKind.PARKING_SPACE && r.getVehicleType() == VehicleType.MOTORCYCLE).map(this::toResponse).toList(),
      resources.stream().filter(r -> r.getKind() == ResourceKind.KEY_SLOT).map(this::toResponse).toList(),
      resources.stream().filter(r -> r.getKind() == ResourceKind.HELMET_LOCKER).map(this::toResponse).toList()
    );
  }

  private ResourceResponse toResponse(ParkingResource resource) {
    return new ResourceResponse(resource.getCode(), resource.getKind(), resource.getVehicleType(), resource.isOccupied(), resource.isReserved(), resource.getAssignedPlate());
  }

  @Transactional
  public void ensureDefaults(UUID tenantId) {
    TenantConfigurationClient.BranchCapacity capacity = tenantConfigurationClient.findBranchCapacity(tenantId);
    syncResourceSet(tenantId, ResourceKind.PARKING_SPACE, VehicleType.CAR, "CARROS", "C", capacity.carCapacity());
    syncResourceSet(tenantId, ResourceKind.PARKING_SPACE, VehicleType.MOTORCYCLE, "MOTOS", "M", capacity.motorcycleCapacity());
    syncResourceSet(tenantId, ResourceKind.KEY_SLOT, null, "LLAVES", "T", capacity.keyRackCapacity());
    syncResourceSet(tenantId, ResourceKind.HELMET_LOCKER, null, "CASCOS", "K", capacity.helmetLockerCapacity());
  }

  private String code(String prefix, int number) {
    return prefix + String.format("%02d", number);
  }

  private void syncResourceSet(UUID tenantId, ResourceKind kind, VehicleType vehicleType, String zoneCode, String prefix, int desiredCount) {
    List<ParkingResource> currentResources = currentResources(tenantId, kind, vehicleType);
    Map<String, ParkingResource> currentByCode = currentResources.stream()
      .collect(java.util.stream.Collectors.toMap(ParkingResource::getCode, Function.identity()));

    for (int i = 1; i <= desiredCount; i++) {
      String code = code(prefix, i);
      ParkingResource existing = currentByCode.get(code);
      if (existing != null) {
        existing.setZoneCode(zoneCode);
        existing.setVehicleType(vehicleType);
        continue;
      }
      resourceRepository.save(ParkingResource.builder()
        .tenantId(tenantId)
        .kind(kind)
        .vehicleType(vehicleType)
        .zoneCode(zoneCode)
        .code(code)
        .build());
    }

    List<ParkingResource> removable = currentResources.stream()
      .filter(resource -> extractSequence(resource.getCode()) > desiredCount)
      .filter(resource -> !resource.isOccupied() && !resource.isReserved())
      .toList();

    if (!removable.isEmpty()) {
      resourceRepository.deleteAll(removable);
    }
  }

  private List<ParkingResource> currentResources(UUID tenantId, ResourceKind kind, VehicleType vehicleType) {
    return resourceRepository.findByTenantIdAndKindOrderByCode(tenantId, kind).stream()
      .filter(resource -> vehicleType == null || resource.getVehicleType() == vehicleType)
      .toList();
  }

  private int extractSequence(String code) {
    String digits = code == null ? "" : code.replaceAll("\\D+", "");
    if (digits.isBlank()) return 0;
    return Integer.parseInt(digits);
  }
}
