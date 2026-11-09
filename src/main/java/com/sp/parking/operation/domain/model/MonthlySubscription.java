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
  /** false cuando se cancelo; una mensualidad vencida sigue activa hasta que se renueve o cancele. */
  private boolean active;
  /** Ultima renovacion (pago que extendio la vigencia). */
  private Instant renewedAt;
  private Instant cancelledAt;
  private String cancelReason;
  private Instant updatedAt;
  /** Vencimiento para el que ya se envio el recordatorio por WhatsApp; al renovar cambia y se recuerda de nuevo. */
  private LocalDate reminderSentFor;
  private Instant reminderSentAt;
  /** Ultimo error al enviar el recordatorio automatico (se reintenta en la siguiente pasada). */
  @Column(length = 500)
  private String reminderError;

  @PrePersist
  void prePersist() {
    if (uuid == null) uuid = UUID.randomUUID();
    if (paidAt == null) paidAt = Instant.now();
    active = true;
  }
}
