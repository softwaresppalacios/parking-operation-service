package com.sp.parking.operation.web.controller;

import com.sp.parking.operation.application.service.MonthlyPaymentNotifier;
import com.sp.parking.operation.application.service.MonthlyReminderService;
import com.sp.parking.operation.application.service.MonthlySubscriptionService;
import com.sp.parking.operation.infraestructure.client.AuditClient;
import com.sp.parking.operation.infraestructure.security.TenantAccessFilter;
import com.sp.parking.operation.web.dto.MonthlySubscriptionDtos.*;
import com.sp.parking.operation.web.dto.TicketDtos.WhatsappStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
@RequestMapping("/api/monthly-subscriptions")
@RequiredArgsConstructor
public class MonthlySubscriptionController {
  private static final Set<String> MANAGERS = Set.of("OWNER", "ADMIN");

  private final MonthlySubscriptionService service;
  private final MonthlyReminderService reminderService;
  private final MonthlyPaymentNotifier paymentNotifier;
  private final AuditClient auditClient;

  @GetMapping
  public List<MonthlySubscriptionResponse> all(@RequestHeader("X-Tenant-Id") UUID tenantId) {
    return service.findAll(tenantId);
  }

  @PostMapping
  public MonthlySubscriptionResponse create(@RequestHeader("X-Tenant-Id") UUID tenantId, @Valid @RequestBody CreateMonthlySubscriptionRequest request) {
    MonthlySubscriptionResponse created = service.create(tenantId, request);
    audit(tenantId, "MONTHLY_CREATE", created, "Cliente " + created.customerName() + " · " + AuditClient.money(created.amount()) + "/mes · vigente " + created.startsAt() + " al " + created.expiresAt());
    // La venta ya quedo guardada con su cobro: se confirma el pago al cliente.
    return created.withPaymentWhatsapp(paymentNotifier.notifyPayment(created, created.amount()));
  }

  /** Editar datos: solo propietario o administrador. */
  @PutMapping("/{uuid}")
  public MonthlySubscriptionResponse update(
    @RequestHeader("X-Tenant-Id") UUID tenantId,
    @RequestAttribute(name = TenantAccessFilter.ROLE_ATTRIBUTE, required = false) String role,
    @PathVariable("uuid") UUID uuid,
    @Valid @RequestBody UpdateMonthlySubscriptionRequest request
  ) {
    requireManager(role, "editar mensualidades");
    MonthlySubscriptionResponse updated = service.update(tenantId, uuid, request);
    audit(tenantId, "MONTHLY_UPDATE", updated, "Cliente " + updated.customerName() + " · " + AuditClient.vehicle(updated.vehicleType()) + " · " + AuditClient.money(updated.amount()) + "/mes");
    return updated;
  }

  /** Renovar es una venta: la puede hacer cualquier rol con acceso al parqueadero. */
  @PostMapping("/{uuid}/renew")
  public MonthlySubscriptionResponse renew(
    @RequestHeader("X-Tenant-Id") UUID tenantId,
    @PathVariable("uuid") UUID uuid,
    @Valid @RequestBody RenewMonthlySubscriptionRequest request
  ) {
    MonthlySubscriptionResponse renewed = service.renew(tenantId, uuid, request);
    BigDecimal paid = request.amount() == null ? renewed.amount().multiply(BigDecimal.valueOf(request.months())) : request.amount();
    audit(tenantId, "MONTHLY_RENEW", renewed, request.months() + (request.months() == 1 ? " mes" : " meses") + " · " + AuditClient.money(paid) + " · vence " + renewed.expiresAt());
    return renewed.withPaymentWhatsapp(paymentNotifier.notifyPayment(renewed, paid));
  }

  /** Cancelar: solo propietario o administrador. */
  @PostMapping("/{uuid}/cancel")
  public MonthlySubscriptionResponse cancel(
    @RequestHeader("X-Tenant-Id") UUID tenantId,
    @RequestAttribute(name = TenantAccessFilter.ROLE_ATTRIBUTE, required = false) String role,
    @PathVariable("uuid") UUID uuid,
    @Valid @RequestBody(required = false) CancelMonthlySubscriptionRequest request
  ) {
    requireManager(role, "cancelar mensualidades");
    MonthlySubscriptionResponse cancelled = service.cancel(tenantId, uuid, request);
    audit(tenantId, "MONTHLY_CANCEL", cancelled, "Cliente " + cancelled.customerName() + (cancelled.cancelReason() == null ? "" : " · Motivo: " + cancelled.cancelReason()));
    return cancelled;
  }

  /** Envia ya el recordatorio de vencimiento por WhatsApp (el automatico sale solo faltando pocos dias). */
  @PostMapping("/{uuid}/reminder")
  public WhatsappStatus sendReminder(@RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable("uuid") UUID uuid) {
    WhatsappStatus status = reminderService.sendNow(tenantId, uuid);
    if (status.sent()) {
      MonthlySubscriptionResponse subscription = service.findOne(tenantId, uuid);
      audit(tenantId, "MONTHLY_REMINDER", subscription, "Recordatorio por WhatsApp a " + subscription.customerName() + " · vence " + subscription.expiresAt());
    }
    return status;
  }

  private void audit(UUID tenantId, String action, MonthlySubscriptionResponse subscription, String details) {
    auditClient.record(tenantId, action, "MONTHLY", subscription.plate(), details);
  }

  private void requireManager(String role, String action) {
    if (role == null || !MANAGERS.contains(role)) {
      throw new ResponseStatusException(FORBIDDEN, "Solo el propietario o un administrador pueden " + action);
    }
  }
}
