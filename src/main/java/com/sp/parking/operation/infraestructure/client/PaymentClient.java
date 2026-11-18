package com.sp.parking.operation.infraestructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/**
 * Registra cobros en la caja de payment-cash-service. Se llama dentro de la transaccion de la salida
 * o de la mensualidad: si el pago no entra a caja, la operacion completa se revierte.
 */
@Component
public class PaymentClient {
  private static final String UNAVAILABLE = "No se pudo registrar el pago en caja; la operación no se completó, intenta de nuevo";
  private final RestClient restClient;

  public PaymentClient(RestClient.Builder restClientBuilder, @Value("${services.payment-cash.base-url}") String baseUrl) {
    this.restClient = restClientBuilder.baseUrl(baseUrl).build();
  }

  public void register(UUID tenantId, PaymentRequest request) {
    try {
      restClient.post()
        .uri("/payments")
        .headers(headers -> ServiceCalls.userHeaders(headers, tenantId))
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .toBodilessEntity();
    } catch (HttpClientErrorException ex) {
      throw new ResponseStatusException(ex.getStatusCode(), ServiceCalls.message(ex, UNAVAILABLE));
    } catch (RestClientException ex) {
      throw new ResponseStatusException(SERVICE_UNAVAILABLE, UNAVAILABLE);
    }
  }

  /** concept: PARKING o MONTHLY. method: CASH u ONLINE. */
  public record PaymentRequest(
    String concept,
    UUID referenceUuid,
    BigDecimal amount,
    String method,
    String onlineReference,
    String plate,
    String vehicleType,
    Instant checkedInAt,
    Instant checkedOutAt,
    String description
  ) {}
}
