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
  private String driverName;
  private String driverDocument;
  private String spaceCode;
  private String keySlotCode;
  private String helmetLockerCode;
  /** Segundo casillero de casco: una moto puede dejar hasta dos cascos, uno por casillero. */
  private String secondHelmetLockerCode;
  private boolean monthlySubscription;
  private Instant checkedInAt;
  private Instant checkedOutAt;
  @Enumerated(EnumType.STRING)
  private TicketStatus status;
  private BigDecimal amountDue;
  /** CASH u ONLINE; null si no hubo cobro (mensualidad o valor cero). */
  private String paymentMethod;

  @PrePersist
  void prePersist() {
    if (uuid == null) uuid = UUID.randomUUID();
    if (checkedInAt == null) checkedInAt = Instant.now();
    if (status == null) status = TicketStatus.ACTIVE;
  }
}
