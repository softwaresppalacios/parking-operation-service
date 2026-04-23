package com.sp.parking.operation.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Table(
  uniqueConstraints = @UniqueConstraint(name = "uk_resource_tenant_kind_code", columnNames = {"tenant_id", "kind", "code"})
)
public class ParkingResource {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ResourceKind kind;
  @Column(nullable = false)
  private String code;
  private String zoneCode;
  @Enumerated(EnumType.STRING)
  private VehicleType vehicleType;
  private boolean occupied;
  private boolean reserved;
  private String assignedPlate;
}
