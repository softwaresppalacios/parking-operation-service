package com.sp.parking.operation.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MonthlySubscription {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false, unique = true, updatable = false)
  private UUID uuid;
  @Column(nullable = false)
  private UUID tenantId;
  @Column(nullable = false)
  private String customerName;
  private String customerDocument;
  private String customerPhone;
  @Column(nullable = false)
  private String plate;
  @Enumerated(EnumType.STRING)
  private VehicleType vehicleType;
  @Column(nullable = false)
  private BigDecimal amount;
  private String paymentMethod;
  @Column(nullable = false)
  private LocalDate startsAt;
  @Column(nullable = false)
  private LocalDate expiresAt;
  private Instant paidAt;
  private boolean active;

  @PrePersist
  void prePersist() {
    if (uuid == null) uuid = UUID.randomUUID();
    if (paidAt == null) paidAt = Instant.now();
    active = true;
  }
}
