package com.sp.parking.operation.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.UUID;

@Component
public class TenantConfigurationClient {
  private final RestClient restClient;

  public TenantConfigurationClient(RestClient.Builder restClientBuilder, @Value("${services.tenant-management.base-url}") String tenantBaseUrl) {
    this.restClient = restClientBuilder.baseUrl(tenantBaseUrl).build();
  }

  public BranchCapacity findBranchCapacity(UUID tenantId) {
    List<TenantSummary> tenants = restClient.get()
      .uri("/tenants")
      .retrieve()
      .body(new ParameterizedTypeReference<List<TenantSummary>>() {});

    if (tenants == null) {
      throw new IllegalStateException("Tenant service returned no data");
    }

    return tenants.stream()
      .filter(tenant -> tenant.uuid().equals(tenantId))
      .findFirst()
      .flatMap(tenant -> tenant.branches().stream().findFirst())
      .map(branch -> new BranchCapacity(
        safe(branch.carCapacity()),
        safe(branch.motorcycleCapacity()),
        safe(branch.keyRackCapacity()),
        safe(branch.helmetLockerCapacity())
      ))
      .orElse(new BranchCapacity(0, 0, 0, 0));
  }

  private int safe(Integer value) {
    return value == null ? 0 : Math.max(0, value);
  }

  public record BranchCapacity(int carCapacity, int motorcycleCapacity, int keyRackCapacity, int helmetLockerCapacity) {}
  public record TenantSummary(UUID uuid, List<BranchSummary> branches) {}
  public record BranchSummary(UUID uuid, Integer carCapacity, Integer motorcycleCapacity, Integer keyRackCapacity, Integer helmetLockerCapacity) {}
}
