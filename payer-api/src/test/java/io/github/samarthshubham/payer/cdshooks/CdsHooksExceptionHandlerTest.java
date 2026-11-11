package io.github.samarthshubham.payer.cdshooks;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CdsHooksExceptionHandlerTest {

  private final CdsHooksExceptionHandler handler = new CdsHooksExceptionHandler();

  @Test
  void payerHapiDownIs503OperationOutcome() {
    ResponseEntity<String> response =
        handler.handlePayerHapiDown(new FhirClientConnectionException("refused"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).contains("\"OperationOutcome\"", "\"transient\"");
  }

  @Test
  void payerHapiErrorIs502WithoutItsDiagnostics() {
    ResponseEntity<String> response =
        handler.handlePayerHapiError(new InternalErrorException("secret detail"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(response.getBody()).contains("HTTP 500").doesNotContain("secret detail");
  }

  @Test
  void hookErrorsKeepTheirStatusAndIssueType() {
    ResponseEntity<String> response =
        handler.handleHookError(new CdsHookException(HttpStatus.PRECONDITION_FAILED, "missing"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
    assertThat(response.getBody()).contains("\"incomplete\"", "missing");
  }

  @Test
  void statusOfMatchesTheHandlers() {
    assertThat(CdsHooksExceptionHandler.statusOf(new FhirClientConnectionException("x")))
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(CdsHooksExceptionHandler.statusOf(new InternalErrorException("x")))
        .isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(CdsHooksExceptionHandler.statusOf(new IllegalStateException("x")))
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(CdsHooksExceptionHandler.statusOf(new CdsHookException(HttpStatus.NOT_FOUND, "x")))
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
