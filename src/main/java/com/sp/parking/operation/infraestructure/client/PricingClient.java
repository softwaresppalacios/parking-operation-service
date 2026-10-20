package com.sp.parking.operation.infraestructure.client;

import com.sp.parking.operation.domain.model.ParkingTicket;
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

/** Tarifa de un ticket segun pricing-service: el valor a cobrar lo decide el servidor, no el navegador. */
@Component
public class PricingClient {
  private static final String UNAVAILABLE = "No se pudo calcular la tarifa del vehículo; intenta de nuevo";
  private final RestClient restClient;

  public PricingClient(RestClient.Builder restClientBuilder, @Value("${services.pricing.base-url}") String baseUrl) {
    this.restClient = restClientBuilder.baseUrl(baseUrl).build();
  }

  public BigDecimal amountDue(UUID tenantId, ParkingTicket ticket) {
    PricingResult result;
    try {
      result = restClient.post()
        .uri("/pricing/calculate")
        .headers(headers -> ServiceCalls.userHeaders(headers, tenantId))
        .contentType(MediaType.APPLICATION_JSON)
        .body(new PricingRequest(ticket.getUuid(), ticket.getVehicleType().name(), ticket.getCheckedInAt(), false))
        .retrieve()
        .body(PricingResult.class);
    } catch (HttpClientErrorException ex) {
      throw new ResponseStatusException(ex.getStatusCode(), ServiceCalls.message(ex, UNAVAILABLE));
    } catch (RestClientException ex) {
      throw new ResponseStatusException(SERVICE_UNAVAILABLE, UNAVAILABLE);
    }
    if (result == null || result.total() == null) throw new ResponseStatusException(SERVICE_UNAVAILABLE, UNAVAILABLE);
    return result.total().max(BigDecimal.ZERO);
  }

  record PricingRequest(UUID ticketUuid, String vehicleType, Instant checkedInAt, boolean lostTicket) {}
  record PricingResult(BigDecimal total) {}
}
