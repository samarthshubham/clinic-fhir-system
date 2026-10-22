package io.github.samarthshubham.clinic.exception;

import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse(404, ex.getMessage()));
  }

  @ExceptionHandler(ReferenceValidationException.class)
  public ResponseEntity<ErrorResponse> handleReferenceValidation(ReferenceValidationException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(new ErrorResponse(422, ex.getMessage()));
  }

  @ExceptionHandler(UnauthorizedWebhookException.class)
  public ResponseEntity<ErrorResponse> handleUnauthorizedWebhook(UnauthorizedWebhookException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse(401, ex.getMessage()));
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse(401, ex.getMessage()));
  }

  @ExceptionHandler(FhirClientConnectionException.class)
  public ResponseEntity<ErrorResponse> handleFhirConnectionError(FhirClientConnectionException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ErrorResponse(503, "FHIR server unavailable: " + ex.getMessage()));
  }

  @ExceptionHandler(BaseServerResponseException.class)
  public ResponseEntity<ErrorResponse> handleFhirError(BaseServerResponseException ex) {
    int status = ex.getStatusCode();
    return ResponseEntity.status(status).body(new ErrorResponse(status, ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
    return ResponseEntity.badRequest().body(new ErrorResponse(400, message));
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ErrorResponse> handleParameterValidation(
      HandlerMethodValidationException ex) {
    String message =
        ex.getParameterValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            error ->
                                result.getMethodParameter().getParameterName()
                                    + " "
                                    + error.getDefaultMessage()))
            .collect(Collectors.joining(", "));
    return ResponseEntity.badRequest().body(new ErrorResponse(400, message));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    return ResponseEntity.badRequest()
        .body(new ErrorResponse(400, "Invalid value for parameter '" + ex.getName() + "'"));
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingParameter(
      MissingServletRequestParameterException ex) {
    return ResponseEntity.badRequest()
        .body(new ErrorResponse(400, "Missing required parameter '" + ex.getParameterName() + "'"));
  }
}
