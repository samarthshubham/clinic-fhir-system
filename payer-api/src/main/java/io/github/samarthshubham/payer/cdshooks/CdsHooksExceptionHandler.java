package io.github.samarthshubham.payer.cdshooks;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CdsServicesController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CdsHooksExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(CdsHooksExceptionHandler.class);

  @ExceptionHandler(CdsHookException.class)
  public ResponseEntity<String> handleHookError(CdsHookException ex) {
    return outcome(ex.status(), ex.getMessage());
  }

  @ExceptionHandler(FhirClientConnectionException.class)
  public ResponseEntity<String> handlePayerHapiDown(FhirClientConnectionException ex) {
    log.warn("Payer FHIR server unavailable during a CDS hook: {}", ex.getMessage());
    return outcome(HttpStatus.SERVICE_UNAVAILABLE, "Payer FHIR server unavailable");
  }

  @ExceptionHandler(BaseServerResponseException.class)
  public ResponseEntity<String> handlePayerHapiError(BaseServerResponseException ex) {
    log.warn("Payer FHIR server returned HTTP {} during a CDS hook", ex.getStatusCode());
    return outcome(HttpStatus.BAD_GATEWAY, "Payer FHIR server returned HTTP " + ex.getStatusCode());
  }

  static HttpStatus statusOf(RuntimeException e) {
    return switch (e) {
      case CdsHookException hook -> hook.status();
      case FhirClientConnectionException connection -> HttpStatus.SERVICE_UNAVAILABLE;
      case BaseServerResponseException response -> HttpStatus.BAD_GATEWAY;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }

  private ResponseEntity<String> outcome(HttpStatus status, String diagnostics) {
    OperationOutcome outcome = new OperationOutcome();
    outcome
        .addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setCode(issueType(status))
        .setDiagnostics(diagnostics);
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(FhirContext.forR4Cached().newJsonParser().encodeResourceToString(outcome));
  }

  private static OperationOutcome.IssueType issueType(HttpStatus status) {
    return switch (status) {
      case NOT_FOUND -> OperationOutcome.IssueType.NOTFOUND;
      case PRECONDITION_FAILED -> OperationOutcome.IssueType.INCOMPLETE;
      case BAD_GATEWAY, SERVICE_UNAVAILABLE -> OperationOutcome.IssueType.TRANSIENT;
      default -> OperationOutcome.IssueType.INVALID;
    };
  }
}
