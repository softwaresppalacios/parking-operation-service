package com.sp.parking.operation.application.service;

import com.sp.parking.operation.infraestructure.security.TenantAccessFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
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

  public BranchResources findBranchResources(UUID tenantId) {
    List<TenantSummary> tenants = restClient.get()
      .uri("/tenants")
      .headers(headers -> {
        String authorization = TenantAccessFilter.currentAuthorization();
        if (authorization != null) headers.set(HttpHeaders.AUTHORIZATION, authorization);
      })
      .retrieve()
      .body(new ParameterizedTypeReference<List<TenantSummary>>() {});

    if (tenants == null) {
      throw new IllegalStateException("Tenant service returned no data");
    }

    return tenants.stream()
      .filter(tenant -> tenant.uuid().equals(tenantId))
      .findFirst()
      .flatMap(tenant -> tenant.branches().stream().findFirst().map(branch -> new BranchResources(
        branch.name() == null || branch.name().isBlank() ? tenant.name() : branch.name(),
        safeManageResources(branch.manageResources()),
        safe(branch.carCapacity()),
        safe(branch.motorcycleCapacity()),
        safe(branch.keyRackCapacity()),
        safe(branch.helmetLockerCapacity())
      )))
      .orElse(new BranchResources(null, true, 0, 0, 0, 0));
  }

  private boolean safeManageResources(Boolean value) {
    return value == null || value;
  }

  private int safe(Integer value) {
    return value == null ? 0 : Math.max(0, value);
  }

  /** name: nombre del establecimiento (la sede), o el del parqueadero si la sede no tiene. */
  public record BranchResources(
    String name,
    boolean manageResources,
    int carCapacity,
    int motorcycleCapacity,
    int keyRackCapacity,
    int helmetLockerCapacity
  ) {}
  public record TenantSummary(UUID uuid, String name, List<BranchSummary> branches) {}
  public record BranchSummary(
    UUID uuid,
    String name,
    Boolean manageResources,
    Integer carCapacity,
    Integer motorcycleCapacity,
    Integer keyRackCapacity,
    Integer helmetLockerCapacity
  ) {}
}
