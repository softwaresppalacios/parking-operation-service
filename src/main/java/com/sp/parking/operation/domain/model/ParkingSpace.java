package com.sp.parking.operation.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ParkingSpace {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false)
  private UUID tenantId;
  @Column(nullable = false)
  private String zoneCode;
  @Column(nullable = false)
  private String code;
  @Enumerated(EnumType.STRING)
  private VehicleType vehicleType;
  private boolean occupied;
}

