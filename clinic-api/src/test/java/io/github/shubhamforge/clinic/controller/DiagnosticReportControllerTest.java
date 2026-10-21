package io.github.shubhamforge.clinic.controller;

import static io.github.shubhamforge.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.shubhamforge.clinic.dto.DiagnosticReportRequest;
import io.github.shubhamforge.clinic.exception.ReferenceValidationException;
import io.github.shubhamforge.clinic.service.DiagnosticReportService;
import java.time.LocalDate;
import java.util.List;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(DiagnosticReportController.class)
class DiagnosticReportControllerTest {

  private static final String VALID_REPORT =
      """
      {"patientId":"p1","encounterId":"e1","serviceRequestId":"sr1","title":"Lipid panel",
       "status":"final","issued":"2024-06-01","conclusion":"Normal","resultIds":["o1"]}
      """;

  @Autowired private MockMvcTester mvc;

  @MockitoBean private DiagnosticReportService diagnosticReportService;

  @Test
  void createReturnsCreatedWithFhirReport() {
    when(diagnosticReportService.createDiagnosticReport(any()))
        .thenReturn(new DiagnosticReport().setConclusion("Normal"));

    assertThat(
            mvc.post()
                .uri("/api/diagnostic-reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REPORT))
        .hasStatus(HttpStatus.CREATED)
        .hasContentType("application/fhir+json")
        .bodyJson()
        .extractingPath("$.conclusion")
        .isEqualTo("Normal");
    verify(diagnosticReportService)
        .createDiagnosticReport(
            new DiagnosticReportRequest(
                "p1",
                "e1",
                "sr1",
                "Lipid panel",
                "final",
                LocalDate.of(2024, 6, 1),
                "Normal",
                List.of("o1")));
  }

  @Test
  void createWithUnknownServiceRequestReturnsUnprocessableEntity() {
    when(diagnosticReportService.createDiagnosticReport(any()))
        .thenThrow(new ReferenceValidationException("ServiceRequest", "sr1"));

    assertThat(
            mvc.post()
                .uri("/api/diagnostic-reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REPORT))
        .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void createWithoutIssuedReturnsBadRequest() {
    assertThat(
            mvc.post()
                .uri("/api/diagnostic-reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","title":"Lipid panel","status":"final"}
                    """))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("must not be null");
    verifyNoInteractions(diagnosticReportService);
  }

  @Test
  void listReturnsReportsForPatient() {
    when(diagnosticReportService.getDiagnosticReportsForPatient("p1"))
        .thenReturn(bundleOf(new DiagnosticReport().setConclusion("Normal")));

    assertThat(mvc.get().uri("/api/diagnostic-reports").param("patientId", "p1"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.entry[0].resource.conclusion")
        .isEqualTo("Normal");
  }

  @Test
  void listWithoutPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/diagnostic-reports")).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(diagnosticReportService);
  }
}
