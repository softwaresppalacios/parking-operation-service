package com.sp.parking.operation.application.service;

import com.sp.parking.operation.domain.model.MonthlySubscription;
import com.sp.parking.operation.domain.repository.MonthlySubscriptionRepository;
import com.sp.parking.operation.infraestructure.client.WhatsappClient;
import com.sp.parking.operation.web.dto.TicketDtos.WhatsappStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Recordatorio de vencimiento de mensualidad por WhatsApp con la plantilla "recordatorio_mensualidad_parqueadero".
 * Variables en orden: Parqueadero, Fecha de vencimiento.
 * Se envia una sola vez por vencimiento, cuando faltan {@code days-before} dias o menos; al renovar cambia
 * el vencimiento y el siguiente periodo se recuerda de nuevo.
 */
@Slf4j
@Service
public class MonthlyReminderService {
  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"));

  private final MonthlySubscriptionRepository repository;
  private final TenantConfigurationClient tenantConfigurationClient;
  private final WhatsappClient whatsappClient;
  private final boolean enabled;
  private final String templateName;
  private final int daysBefore;

  public MonthlyReminderService(
    MonthlySubscriptionRepository repository,
    TenantConfigurationClient tenantConfigurationClient,
    WhatsappClient whatsappClient,
    @Value("${whatsapp.reminder.enabled:true}") boolean enabled,
    @Value("${whatsapp.reminder.template-name}") String templateName,
    @Value("${whatsapp.reminder.days-before:5}") int daysBefore
  ) {
    this.repository = repository;
    this.tenantConfigurationClient = tenantConfigurationClient;
    this.whatsappClient = whatsappClient;
    this.enabled = enabled;
    this.templateName = templateName.trim();
    this.daysBefore = daysBefore;
  }

  /** Pasada automatica: recuerda las mensualidades que vencen en los proximos dias y aun no tienen recordatorio. */
  @Scheduled(cron = "${whatsapp.reminder.cron:0 0 8-19 * * *}", zone = "America/Bogota")
  public void sendDueReminders() {
    if (!enabled || !whatsappClient.isEnabled() || !whatsappClient.isConfigured()) return;
    LocalDate today = LocalDate.now(BOGOTA);
    List<MonthlySubscription> due = repository.findByActiveTrueAndExpiresAtBetweenOrderByExpiresAt(today, today.plusDays(daysBefore)).stream()
      .filter(subscription -> !subscription.getExpiresAt().equals(subscription.getReminderSentFor()))
      .filter(subscription -> subscription.getCustomerPhone() != null && !subscription.getCustomerPhone().isBlank())
      .toList();
    if (due.isEmpty()) return;

    Map<UUID, String> parkingNames = new HashMap<>();
    int sent = 0;
    for (MonthlySubscription subscription : due) {
      String parkingName = parkingNames.computeIfAbsent(subscription.getTenantId(), this::parkingName);
      WhatsappClient.Result result = send(subscription, parkingName);
      if (result.sent()) {
        sent++;
      } else if (isChannelProblem(result.error())) {
        // Plantilla sin aprobar, token vencido...: fallaria igual con todos; se reintenta en la siguiente pasada.
        log.warn("Recordatorios de mensualidad detenidos: {}", result.error());
        break;
      }
    }
    log.info("Recordatorios de mensualidad: {} enviados de {} pendientes", sent, due.size());
  }

  /** Envio manual desde la pantalla de mensualidades (p. ej. para reintentar despues de corregir el celular). */
  public WhatsappStatus sendNow(UUID tenantId, UUID uuid) {
    if (!whatsappClient.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, WhatsappClient.PAUSED_MESSAGE);
    }
    MonthlySubscription subscription = repository.findByTenantIdAndUuid(tenantId, uuid)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensualidad no encontrada"));
    if (!subscription.isActive()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta mensualidad está cancelada");
    }
    if (subscription.getCustomerPhone() == null || subscription.getCustomerPhone().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La mensualidad no tiene celular del cliente");
    }
    WhatsappClient.Result result = send(subscription, parkingName(tenantId));
    return new WhatsappStatus(result.sent(), result.error());
  }

  private WhatsappClient.Result send(MonthlySubscription subscription, String parkingName) {
    WhatsappClient.Result result = whatsappClient.sendTemplate(templateName, subscription.getCustomerPhone(), List.of(
      parkingName,
      EXPIRY.format(subscription.getExpiresAt())
    ));
    if (result.sent()) {
      subscription.setReminderSentFor(subscription.getExpiresAt());
      subscription.setReminderSentAt(Instant.now());
      subscription.setReminderError(null);
    } else {
      subscription.setReminderError(truncate(result.error()));
    }
    repository.save(subscription);
    return result;
  }

  private String parkingName(UUID tenantId) {
    try {
      String name = tenantConfigurationClient.findBranchResources(tenantId).name();
      return name == null || name.isBlank() ? "SP Parqueaderos" : name;
    } catch (RuntimeException ex) {
      return "SP Parqueaderos";
    }
  }

  /** Errores que no dependen del cliente (celular), sino de la cuenta o la plantilla. */
  private static boolean isChannelProblem(String error) {
    if (error == null) return false;
    String text = error.toLowerCase(Locale.ROOT);
    return text.contains("plantilla") || text.contains("token") || text.contains("bloqueada")
      || text.contains("no se pudo contactar") || text.contains("pausa") || text.contains("configurado");
  }

  private static String truncate(String error) {
    if (error == null) return "No se pudo enviar el recordatorio";
    return error.length() > 500 ? error.substring(0, 500) : error;
  }
}
