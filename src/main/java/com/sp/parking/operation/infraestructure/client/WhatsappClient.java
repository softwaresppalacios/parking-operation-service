package com.sp.parking.operation.infraestructure.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.List;
import java.util.Map;

/**
 * Envia plantillas aprobadas por la API de WhatsApp Cloud (Meta). Nunca lanza excepciones:
 * un fallo de WhatsApp no debe impedir registrar la operacion del parqueadero.
 */
@Slf4j
@Component
public class WhatsappClient {
  public static final String PAUSED_MESSAGE = "El envío por WhatsApp está en pausa";
  private final RestClient restClient;
  private final String phoneNumberId;
  private final String accessToken;
  private final String templateName;
  private final String templateLanguage;
  private final String defaultCountryCode;
  private final boolean enabled;

  public WhatsappClient(
    RestClient.Builder restClientBuilder,
    @Value("${whatsapp.api-url}") String apiUrl,
    @Value("${whatsapp.phone-number-id:}") String phoneNumberId,
    @Value("${whatsapp.access-token:}") String accessToken,
    @Value("${whatsapp.template-name}") String templateName,
    @Value("${whatsapp.template-language}") String templateLanguage,
    @Value("${whatsapp.default-country-code:57}") String defaultCountryCode,
    @Value("${whatsapp.enabled:true}") boolean enabled
  ) {
    SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
    timeouts.setConnectTimeout(5_000);
    timeouts.setReadTimeout(10_000);
    this.restClient = restClientBuilder.baseUrl(apiUrl).requestFactory(timeouts).build();
    this.phoneNumberId = phoneNumberId.trim();
    this.accessToken = accessToken.trim();
    this.templateName = templateName.trim();
    this.templateLanguage = templateLanguage.trim();
    this.defaultCountryCode = defaultCountryCode.trim();
    this.enabled = enabled;
  }

  public record Result(boolean sent, String messageId, String error) {
    static Result failed(String error) { return new Result(false, null, error); }
  }

  /** Pausado a proposito (WHATSAPP_ENABLED=false), aunque haya token. */
  public boolean isEnabled() {
    return enabled;
  }

  public boolean isConfigured() {
    return !phoneNumberId.isEmpty() && !accessToken.isEmpty();
  }

  /** Envia la plantilla configurada con sus variables en orden ({{1}}, {{2}}, ...). */
  public Result sendTemplate(String phone, List<String> parameters) {
    if (!enabled) return Result.failed(PAUSED_MESSAGE);
    if (!isConfigured()) return Result.failed("El envío por WhatsApp no está configurado en el servidor");
    String to = normalizePhone(phone);
    if (to.length() < 10) return Result.failed("El celular del conductor no es válido para WhatsApp");

    Map<String, Object> body = Map.of(
      "messaging_product", "whatsapp",
      "to", to,
      "type", "template",
      "template", Map.of(
        "name", templateName,
        "language", Map.of("code", templateLanguage),
        "components", List.of(Map.of(
          "type", "body",
          "parameters", parameters.stream().map(value -> Map.of("type", "text", "text", sanitize(value))).toList()
        ))
      )
    );
    try {
      Map<?, ?> response = restClient.post()
        .uri("/{phoneNumberId}/messages", phoneNumberId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Map.class);
      String messageId = messageId(response);
      log.info("Ticket enviado por WhatsApp a {} (mensaje {})", to, messageId);
      return new Result(true, messageId, null);
    } catch (HttpStatusCodeException ex) {
      String error = metaError(ex);
      log.warn("WhatsApp rechazó el envío a {}: {}", to, error);
      return Result.failed(error);
    } catch (RestClientException ex) {
      log.warn("No se pudo contactar WhatsApp: {}", ex.getMessage());
      return Result.failed("No se pudo contactar el servicio de WhatsApp");
    }
  }

  /** 10 digitos (celular de Colombia) se completan con el indicativo del pais. */
  private String normalizePhone(String phone) {
    String digits = phone == null ? "" : phone.replaceAll("\\D", "");
    return digits.length() == 10 ? defaultCountryCode + digits : digits;
  }

  /** Meta rechaza variables vacias, con saltos de linea, tabulaciones o mas de 4 espacios seguidos. */
  private static String sanitize(String value) {
    String clean = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {2,}", " ").trim();
    return clean.isEmpty() ? "—" : clean;
  }

  private static String messageId(Map<?, ?> response) {
    if (response != null && response.get("messages") instanceof List<?> messages && !messages.isEmpty()
      && messages.get(0) instanceof Map<?, ?> first && first.get("id") != null) {
      return first.get("id").toString();
    }
    return null;
  }

  /** Errores de Meta frecuentes, explicados para quien opera la caja. */
  private static final Map<Integer, String> KNOWN_ERRORS = Map.of(
    132001, "la plantilla no existe con ese nombre e idioma (revisa WHATSAPP_TEMPLATE_NAME y WHATSAPP_TEMPLATE_LANGUAGE)",
    132000, "el número de variables no coincide con la plantilla aprobada",
    132012, "el formato de las variables no coincide con la plantilla aprobada",
    132015, "la plantilla está pausada en Meta",
    132016, "la plantilla está deshabilitada en Meta",
    131030, "el número no está en la lista de destinatarios permitidos de la app de Meta",
    131026, "el número no tiene WhatsApp o no puede recibir el mensaje",
    190, "el token de acceso no es válido o venció",
    131031, "la cuenta de WhatsApp Business está bloqueada"
  );

  private static String metaError(HttpStatusCodeException ex) {
    try {
      Map<?, ?> body = ex.getResponseBodyAs(Map.class);
      if (body != null && body.get("error") instanceof Map<?, ?> error) {
        Object details = error.get("error_data") instanceof Map<?, ?> data ? data.get("details") : null;
        Object message = error.get("message");
        String known = error.get("code") instanceof Number code ? KNOWN_ERRORS.get(code.intValue()) : null;
        Object raw = details != null ? details : message != null ? message : ex.getStatusText();
        return "WhatsApp: " + (known != null ? known + " (" + raw + ")" : raw);
      }
    } catch (RuntimeException ignored) {
      // Respuesta sin JSON: se usa el estado HTTP.
    }
    return "WhatsApp respondió " + ex.getStatusCode().value();
  }
}
