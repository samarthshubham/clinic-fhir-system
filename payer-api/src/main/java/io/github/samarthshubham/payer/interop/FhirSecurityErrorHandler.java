package io.github.samarthshubham.payer.interop;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

public class FhirSecurityErrorHandler implements AuthenticationEntryPoint {

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    OperationOutcome outcome = new OperationOutcome();
    outcome
        .addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setCode(OperationOutcome.IssueType.LOGIN)
        .setDiagnostics("Invalid or expired token");
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(Constants.CT_FHIR_JSON_NEW);
    response
        .getWriter()
        .write(FhirContext.forR4Cached().newJsonParser().encodeResourceToString(outcome));
  }
}
