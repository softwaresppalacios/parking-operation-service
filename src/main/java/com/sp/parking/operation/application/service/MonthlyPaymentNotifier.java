package com.sp.parking.operation.application.service;

import com.sp.parking.operation.infraestructure.client.WhatsappClient;
import com.sp.parking.operation.web.dto.MonthlySubscriptionDtos.MonthlySubscriptionResponse;
import com.sp.parking.operation.web.dto.TicketDtos.WhatsappStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

/**
 * Confirma al cliente por WhatsApp que su pago de mensualidad fue recibido (plantilla de Meta
 * {@code notificacion_pago_mensualidad}: {{1}} nombre del cliente, {{2}} placa).
 * Se llama despues de guardar la venta o la renovacion: si el cobro fallo y se revirtio, no se avisa.
 * Un fallo de WhatsApp nunca anula la venta; solo se informa en la respuesta.
 */
@Service
public class MonthlyPaymentNotifier {
  private final WhatsappClient whatsapp;
  private final boolean enabled;
  private final String templateName;

  public MonthlyPaymentNotifier(
    WhatsappClient whatsapp,
    @Value("${whatsapp.payment.enabled:true}") boolean enabled,
    @Value("${whatsapp.payment.template-name:notificacion_pago_mensualidad}") String templateName
  ) {
    this.whatsapp = whatsapp;
    this.enabled = enabled;
    this.templateName = templateName.trim();
  }

  /** null cuando no corresponde enviar: confirmacion desactivada, sin valor cobrado o cliente sin celular. */
  public WhatsappStatus notifyPayment(MonthlySubscriptionResponse subscription, BigDecimal paidAmount) {
    if (!enabled || paidAmount == null || paidAmount.signum() <= 0) return null;
    String phone = subscription.customerPhone();
    if (phone == null || phone.isBlank()) return null;
    WhatsappClient.Result result = whatsapp.sendTemplate(templateName, phone, List.of(subscription.customerName(), displayPlate(subscription.plate())));
    return new WhatsappStatus(result.sent(), result.error());
  }

  /** ABC123 -> ABC 123, como aparece en la placa. */
  static String displayPlate(String plate) {
    String clean = plate == null ? "" : plate.toUpperCase().replaceAll("[^A-Z0-9]", "");
    return clean.length() > 3 ? clean.substring(0, 3) + " " + clean.substring(3) : clean;
  }
}
