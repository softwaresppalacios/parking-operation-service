package com.sp.parking.operation.web.exception;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> responseStatus(ResponseStatusException ex) {
    return ResponseEntity.status(ex.getStatusCode()).body(body(ex.getStatusCode(), ex.getReason()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> generic(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage()));
  }

  private Map<String, Object> body(HttpStatusCode status, String message) {
    return Map.of(
      "timestamp", Instant.now().toString(),
      "status", status.value(),
      "error", status instanceof HttpStatus httpStatus ? httpStatus.getReasonPhrase() : "Error",
      "message", message == null ? "Unexpected error" : message
    );
  }
}
