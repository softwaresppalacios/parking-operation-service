package com.sp.parking.operation.infraestructure.client;

import com.sp.parking.operation.infraestructure.security.TenantAccessFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import java.util.Map;
import java.util.UUID;

/** Encabezados y errores comunes de las llamadas a otros microservicios en nombre del usuario. */
final class ServiceCalls {
  private ServiceCalls() {}

  static void userHeaders(HttpHeaders headers, UUID tenantId) {
    headers.set(TenantAccessFilter.TENANT_HEADER, tenantId.toString());
    String authorization = TenantAccessFilter.currentAuthorization();
    if (authorization != null) headers.set(HttpHeaders.AUTHORIZATION, authorization);
  }

  /** El "message" que devolvio el otro servicio, o el texto por defecto. */
  static String message(HttpClientErrorException ex, String fallback) {
    try {
      Map<?, ?> body = ex.getResponseBodyAs(Map.class);
      Object message = body == null ? null : body.get("message");
      return message == null ? fallback : message.toString();
    } catch (RuntimeException ignored) {
      return fallback;
    }
  }
}
