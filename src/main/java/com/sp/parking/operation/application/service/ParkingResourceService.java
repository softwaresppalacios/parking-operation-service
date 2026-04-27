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
    TenantConfigurationClient.BranchResources config = tenantConfigurationClient.findBranchResources(tenantId);
    if (!config.manageResources()) {
      return;
    }
    syncResourceSet(tenantId, ResourceKind.PARKING_SPACE, VehicleType.CAR, "CARROS", codesOrDefaults("C", config.carCapacity()));
    syncResourceSet(tenantId, ResourceKind.PARKING_SPACE, VehicleType.MOTORCYCLE, "MOTOS", codesOrDefaults("M", config.motorcycleCapacity()));
    syncResourceSet(tenantId, ResourceKind.KEY_SLOT, null, "LLAVES", codesOrDefaults("T", config.keyRackCapacity()));
    syncResourceSet(tenantId, ResourceKind.HELMET_LOCKER, null, "CASCOS", codesOrDefaults("K", config.helmetLockerCapacity()));
  }

  private void syncResourceSet(UUID tenantId, ResourceKind kind, VehicleType vehicleType, String zoneCode, List<String> desiredCodes) {
    List<ParkingResource> currentResources = currentResources(tenantId, kind, vehicleType);
    Map<String, ParkingResource> currentByCode = currentResources.stream()
      .collect(java.util.stream.Collectors.toMap(ParkingResource::getCode, Function.identity(), (left, right) -> left));

    for (String desiredCode : desiredCodes) {
      String code = desiredCode.toUpperCase(Locale.ROOT);
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
      .filter(resource -> desiredCodes.stream().noneMatch(code -> code.equalsIgnoreCase(resource.getCode())))
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

  private List<String> codesOrDefaults(String prefix, int count) {
    int safeCount = Math.max(0, count);
    return java.util.stream.IntStream.rangeClosed(1, safeCount)
      .mapToObj(i -> prefix + String.format("%02d", i))
      .toList();
  }
}
