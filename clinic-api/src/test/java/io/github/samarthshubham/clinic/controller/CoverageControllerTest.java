package io.github.samarthshubham.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.config.SecurityConfig;
import io.github.samarthshubham.clinic.dto.CoverageRequest;
import io.github.samarthshubham.clinic.dto.CoverageResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.exception.ReferenceValidationException;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.security.Role;
import io.github.samarthshubham.clinic.service.CoverageService;
import io.github.samarthshubham.clinic.support.WithRole;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(CoverageController.class)
@Import(SecurityConfig.class)
@WithRole(Role.ADMIN)
class CoverageControllerTest {

  private static final CoverageResponse PRIYA_COVERAGE =
      new CoverageResponse(
          "cov1",
          "p1",
          "org1",
          "praxis-health-plan",
          "Praxis Health Plan",
          "PHP-10001",
          "Praxis Gold PPO",
          "active",
          LocalDate.of(2026, 1, 1),
          null,
          "self");

  private static final String PRIYA_COVERAGE_JSON =
      """
      {"id":"cov1","patientId":"p1","payerOrganizationId":"org1","payerId":"praxis-health-plan",
       "payerName":"Praxis Health Plan","memberId":"PHP-10001","planName":"Praxis Gold PPO",
       "status":"active","periodStart":"2026-01-01","periodEnd":null,"relationship":"self"}
      """;

  @Autowired private MockMvcTester mvc;

  @MockitoBean private CoverageService coverageService;

  @Test
  void createReturnsCreatedWithCoverageResponse() {
    when(coverageService.createCoverage(any())).thenReturn(PRIYA_COVERAGE);

    assertThat(
            mvc.post()
                .uri("/api/coverages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","payerId":"praxis-health-plan","payerName":"Praxis Health Plan",
                     "memberId":"PHP-10001","planName":"Praxis Gold PPO","status":"active",
                     "periodStart":"2026-01-01"}
                    """))
        .hasStatus(HttpStatus.CREATED)
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo(PRIYA_COVERAGE_JSON);
    verify(coverageService)
        .createCoverage(
            new CoverageRequest(
                "p1",
                "praxis-health-plan",
                "Praxis Health Plan",
                "PHP-10001",
                "Praxis Gold PPO",
                "active",
                LocalDate.of(2026, 1, 1),
                null,
                null));
  }

  @Test
  void createWithoutMemberIdReturnsBadRequest() {
    assertThat(
            mvc.post()
                .uri("/api/coverages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","payerId":"praxis-health-plan","payerName":"Praxis Health Plan",
                     "status":"active","periodStart":"2026-01-01"}
                    """))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"status":400,"message":"must not be blank"}
            """);
    verifyNoInteractions(coverageService);
  }

  @Test
  void createWithUnknownStatusOrRelationshipReturnsBadRequest() {
    assertThat(
            mvc.post()
                .uri("/api/coverages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","payerId":"praxis-health-plan","payerName":"Praxis Health Plan",
                     "memberId":"PHP-10001","status":"lapsed","periodStart":"2026-01-01",
                     "relationship":"cousin"}
                    """))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.message")
        .asString()
        .contains("status must be active, cancelled, draft or entered-in-error")
        .contains("relationship must be a subscriber-relationship code");
    verifyNoInteractions(coverageService);
  }

  @Test
  void createWithQuerySyntaxInPayerIdReturnsBadRequest() {
    assertThat(
            mvc.post()
                .uri("/api/coverages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","payerId":"a&_id=x","payerName":"Praxis Health Plan",
                     "memberId":"PHP-10001","status":"active","periodStart":"2026-01-01"}
                    """))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("payerId may only contain letters, digits, '.', '_' and '-'");
    verifyNoInteractions(coverageService);
  }

  @Test
  void getReturnsCoverageById() {
    when(coverageService.getCoverage("cov1")).thenReturn(PRIYA_COVERAGE);

    assertThat(mvc.get().uri("/api/coverages/cov1"))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(PRIYA_COVERAGE_JSON);
  }

  @Test
  void getUnknownCoverageReturnsNotFound() {
    when(coverageService.getCoverage("missing"))
        .thenThrow(new ResourceNotFoundException("Coverage", "missing"));

    assertThat(mvc.get().uri("/api/coverages/missing"))
        .hasStatus(HttpStatus.NOT_FOUND)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"status":404,"message":"Coverage with id 'missing' not found"}
            """);
  }

  @Test
  void updateReturnsTheUpdatedCoverage() {
    when(coverageService.updateCoverage(eq("cov1"), any())).thenReturn(PRIYA_COVERAGE);

    assertThat(
            mvc.put()
                .uri("/api/coverages/cov1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","payerId":"praxis-health-plan","payerName":"Praxis Health Plan",
                     "memberId":"PHP-10001","planName":"Praxis Gold PPO","status":"active",
                     "periodStart":"2026-01-01","relationship":"self"}
                    """))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(PRIYA_COVERAGE_JSON);
    verify(coverageService)
        .updateCoverage(
            "cov1",
            new CoverageRequest(
                "p1",
                "praxis-health-plan",
                "Praxis Health Plan",
                "PHP-10001",
                "Praxis Gold PPO",
                "active",
                LocalDate.of(2026, 1, 1),
                null,
                "self"));
  }

  @Test
  void updateWithoutPayerNameReturnsBadRequest() {
    assertThat(
            mvc.put()
                .uri("/api/coverages/cov1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","payerId":"praxis-health-plan","memberId":"PHP-10001",
                     "status":"active","periodStart":"2026-01-01"}
                    """))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"status":400,"message":"must not be blank"}
            """);
    verifyNoInteractions(coverageService);
  }

  @Test
  void updateUnknownCoverageReturnsNotFound() {
    when(coverageService.updateCoverage(eq("missing"), any()))
        .thenThrow(new ResourceNotFoundException("Coverage", "missing"));

    assertThat(
            mvc.put()
                .uri("/api/coverages/missing")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","payerId":"praxis-health-plan","payerName":"Praxis Health Plan",
                     "memberId":"PHP-10001","status":"active","periodStart":"2026-01-01"}
                    """))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void updateForAnotherPatientReturnsUnprocessableEntity() {
    when(coverageService.updateCoverage(eq("cov1"), any()))
        .thenThrow(new ReferenceValidationException("Coverage", "cov1", "p2"));

    assertThat(
            mvc.put()
                .uri("/api/coverages/cov1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p2","payerId":"praxis-health-plan","payerName":"Praxis Health Plan",
                     "memberId":"PHP-10001","status":"active","periodStart":"2026-01-01"}
                    """))
        .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"status":422,"message":"Referenced Coverage with id 'cov1' does not belong to Patient 'p2'"}
            """);
  }

  @Test
  void listReturnsThePatientsCoverages() {
    when(coverageService.getCoveragesForPatient("p1"))
        .thenReturn(new PageResponse<>(List.of(PRIYA_COVERAGE), 1));

    assertThat(mvc.get().uri("/api/coverages").param("patientId", "p1"))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo("{\"items\":[" + PRIYA_COVERAGE_JSON + "],\"total\":1}");
  }

  @Test
  void listWithoutPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/coverages")).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(coverageService);
  }
}
