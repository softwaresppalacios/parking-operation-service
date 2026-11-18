package com.sp.parking.operation.domain.model;

import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** Metodos de pago aceptados: CASH (efectivo) y ONLINE (en linea). DIGITAL es el nombre anterior de ONLINE. */
public final class PaymentMethods {
  public static final String CASH = "CASH";
  public static final String ONLINE = "ONLINE";

  private PaymentMethods() {}

  public static String require(String method) {
    String value = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
    return switch (value) {
      case CASH -> CASH;
      case ONLINE, "DIGITAL" -> ONLINE;
      case "" -> throw new ResponseStatusException(BAD_REQUEST, "Selecciona el método de pago: efectivo o en línea");
      default -> throw new ResponseStatusException(BAD_REQUEST, "Método de pago no válido: " + method);
    };
  }
}
