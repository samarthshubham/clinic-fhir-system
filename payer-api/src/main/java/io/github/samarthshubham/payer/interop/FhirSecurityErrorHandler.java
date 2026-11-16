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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

public class FhirSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    if (authException instanceof OAuth2AuthenticationException) {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
      write(
          response,
          HttpStatus.UNAUTHORIZED,
          OperationOutcome.IssueType.LOGIN,
          "Invalid or expired token");
    } else {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
      write(
          response,
          HttpStatus.UNAUTHORIZED,
          OperationOutcome.IssueType.LOGIN,
          "Authentication required");
    }
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    write(
        response,
        HttpStatus.FORBIDDEN,
        OperationOutcome.IssueType.FORBIDDEN,
        "The token does not have the scope this request needs");
  }

  private static void write(
      HttpServletResponse response,
      HttpStatus status,
      OperationOutcome.IssueType type,
      String diagnostics)
      throws IOException {
    OperationOutcome outcome = new OperationOutcome();
    outcome
        .addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setCode(type)
        .setDiagnostics(diagnostics);
    response.setStatus(status.value());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(Constants.CT_FHIR_JSON_NEW);
    response
        .getWriter()
        .write(FhirContext.forR4Cached().newJsonParser().encodeResourceToString(outcome));
  }
}
