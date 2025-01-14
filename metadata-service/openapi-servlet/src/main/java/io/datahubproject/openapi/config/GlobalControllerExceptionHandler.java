package io.datahubproject.openapi.config;

import com.linkedin.metadata.dao.throttle.APIThrottleException;
import io.datahubproject.metadata.exception.ActorAccessException;
import io.datahubproject.openapi.exception.InvalidUrnException;
import io.datahubproject.openapi.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.core.Ordered;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;

@Slf4j
@ControllerAdvice
public class GlobalControllerExceptionHandler extends DefaultHandlerExceptionResolver {

  @PostConstruct
  public void init() {
    log.info("GlobalControllerExceptionHandler initialized");
  }

  public GlobalControllerExceptionHandler() {
    setOrder(Ordered.HIGHEST_PRECEDENCE);
    setWarnLogCategory(getClass().getName());
  }

  @ExceptionHandler({ConversionFailedException.class, ConversionNotSupportedException.class})
  public ResponseEntity<String> handleConflict(RuntimeException ex) {
    return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(InvalidUrnException.class)
  public static ResponseEntity<Map<String, String>> handleUrnException(InvalidUrnException e) {
    return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(APIThrottleException.class)
  public static ResponseEntity<Map<String, String>> handleThrottleException(
      APIThrottleException e) {

    HttpHeaders headers = new HttpHeaders();
    if (e.getDurationMs() >= 0) {
      headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(e.getDurationSeconds()));
    }

    return new ResponseEntity<>(
        Map.of("error", e.getMessage()), headers, HttpStatus.TOO_MANY_REQUESTS);
  }

  @ExceptionHandler(UnauthorizedException.class)
  public static ResponseEntity<Map<String, String>> handleUnauthorizedException(
      UnauthorizedException e) {
    return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.FORBIDDEN);
  }

  @ExceptionHandler(ActorAccessException.class)
  public static ResponseEntity<Map<String, String>> actorAccessException(ActorAccessException e) {
    return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.FORBIDDEN);
  }

  @Override
  protected void logException(Exception ex, HttpServletRequest request) {
    log.error("Error while resolving request: " + request.getRequestURI(), ex);
  }

  @Override
  protected void sendServerError(
      Exception ex, HttpServletRequest request, HttpServletResponse response) throws IOException {
    log.error("Error while resolving request: " + request.getRequestURI(), ex);
    request.setAttribute("jakarta.servlet.error.exception", ex);
    response.sendError(500);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleGenericException(
      Exception e, HttpServletRequest request) {
    log.error("Unhandled exception occurred for request: " + request.getRequestURI(), e);
    return new ResponseEntity<>(
        Map.of("error", "Internal server error occurred"), HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
