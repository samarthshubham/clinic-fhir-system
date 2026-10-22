package io.github.samarthshubham.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.config.SecurityConfig;
import io.github.samarthshubham.clinic.dto.DiagnosticReportRequest;
import io.github.samarthshubham.clinic.dto.DiagnosticReportResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.exception.ReferenceValidationException;
import io.github.samarthshubham.clinic.service.DiagnosticReportService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(DiagnosticReportController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class DiagnosticReportControllerTest {

  private static final String VALID_REPORT =
      """
      {"patientId":"p1","encounterId":"e1","serviceRequestId":"sr1","title":"Lipid panel",
       "status":"final","issued":"2024-06-01","conclusion":"Normal","resultIds":["o1"]}
      """;

  private static final DiagnosticReportResponse LIPID_REPORT =
      new DiagnosticReportResponse(
          "r1",
          "p1",
          "e1",
          "sr1",
          "Lipid panel",
          "final",
          LocalDate.of(2024, 6, 1),
          "Normal",
          List.of("o1"));

  private static final String LIPID_REPORT_JSON =
      """
      {"id":"r1","patientId":"p1","encounterId":"e1","serviceRequestId":"sr1",
       "title":"Lipid panel","status":"final","issued":"2024-06-01","conclusion":"Normal",
       "resultIds":["o1"]}
      """;

  @Autowired private MockMvcTester mvc;

  @MockitoBean private DiagnosticReportService diagnosticReportService;

  @Test
  void createReturnsCreatedWithReportResponse() {
    when(diagnosticReportService.createDiagnosticReport(any())).thenReturn(LIPID_REPORT);

    assertThat(
            mvc.post()
                .uri("/api/diagnostic-reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REPORT))
        .hasStatus(HttpStatus.CREATED)
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo(LIPID_REPORT_JSON);
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
        .thenReturn(new PageResponse<>(List.of(LIPID_REPORT), 1));

    assertThat(mvc.get().uri("/api/diagnostic-reports").param("patientId", "p1"))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo("{\"items\":[" + LIPID_REPORT_JSON + "],\"total\":1}");
  }

  @Test
  void listWithoutPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/diagnostic-reports")).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(diagnosticReportService);
  }
}
