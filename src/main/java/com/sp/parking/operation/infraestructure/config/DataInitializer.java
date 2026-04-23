package com.sp.parking.operation.infraestructure.config;

import com.sp.parking.operation.domain.model.*;
import com.sp.parking.operation.domain.repository.ParkingResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
  private static final UUID DEMO_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private final ParkingResourceRepository resourceRepository;

  @Override
  @Transactional
  public void run(String... args) {
    if (!resourceRepository.findByTenantIdAndKindOrderByCode(DEMO_TENANT_ID, ResourceKind.PARKING_SPACE).isEmpty()) return;
    for (int i = 1; i <= 20; i++) {
      resourceRepository.save(ParkingResource.builder().tenantId(DEMO_TENANT_ID).kind(ResourceKind.PARKING_SPACE).vehicleType(VehicleType.CAR).zoneCode("CARROS").code(code("C", i)).build());
      resourceRepository.save(ParkingResource.builder().tenantId(DEMO_TENANT_ID).kind(ResourceKind.PARKING_SPACE).vehicleType(VehicleType.MOTORCYCLE).zoneCode("MOTOS").code(code("M", i)).build());
      resourceRepository.save(ParkingResource.builder().tenantId(DEMO_TENANT_ID).kind(ResourceKind.KEY_SLOT).zoneCode("LLAVES").code(code("T", i)).build());
      if (i <= 15) resourceRepository.save(ParkingResource.builder().tenantId(DEMO_TENANT_ID).kind(ResourceKind.HELMET_LOCKER).zoneCode("CASCOS").code(code("K", i)).build());
    }
  }

  private String code(String prefix, int number) {
    return prefix + String.format("%02d", number);
  }
}
