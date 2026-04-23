package com.sp.parking.operation.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ParkingTicket {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false, unique = true, updatable = false)
  private UUID uuid;
  @Column(nullable = false)
  private UUID tenantId;
  @Column(nullable = false)
  private String plate;
  @Enumerated(EnumType.STRING)
  private VehicleType vehicleType;
  private String driverPhone;
  private String spaceCode;
  private String keySlotCode;
  private String helmetLockerCode;
  private boolean monthlySubscription;
  private Instant checkedInAt;
  private Instant checkedOutAt;
  @Enumerated(EnumType.STRING)
  private TicketStatus status;
  private BigDecimal amountDue;

  @PrePersist
  void prePersist() {
    if (uuid == null) uuid = UUID.randomUUID();
    if (checkedInAt == null) checkedInAt = Instant.now();
    if (status == null) status = TicketStatus.ACTIVE;
  }
}
