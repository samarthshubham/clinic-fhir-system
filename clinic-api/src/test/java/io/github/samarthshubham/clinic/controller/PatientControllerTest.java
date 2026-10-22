package io.github.samarthshubham.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.config.SecurityConfig;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.dto.PatientRequest;
import io.github.samarthshubham.clinic.dto.PatientResponse;
import io.github.samarthshubham.clinic.dto.SnapshotResponse;
import io.github.samarthshubham.clinic.dto.TimelineEvent;
import io.github.samarthshubham.clinic.dto.TrendsResponse;
import io.github.samarthshubham.clinic.service.PatientService;
import io.github.samarthshubham.clinic.service.SnapshotService;
import io.github.samarthshubham.clinic.service.TimelineService;
import io.github.samarthshubham.clinic.service.TrendsService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(PatientController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class PatientControllerTest {

  private static final String VALID_PATIENT =
      """
      {"firstName":"Priya","lastName":"Nair","dateOfBirth":"1985-03-14","gender":"female",
       "phone":"555-0100","email":"priya@example.com"}
      """;

  private static final PatientResponse PRIYA =
      new PatientResponse(
          "p1", "Priya", "Nair", "1985-03-14", "female", "555-0100", "priya@example.com", true);

  private static final String PRIYA_JSON =
      """
      {"id":"p1","firstName":"Priya","lastName":"Nair","dateOfBirth":"1985-03-14",
       "gender":"female","phone":"555-0100","email":"priya@example.com","active":true}
      """;

  @Autowired private MockMvcTester mvc;

  @MockitoBean private PatientService patientService;
  @MockitoBean private SnapshotService snapshotService;
  @MockitoBean private TrendsService trendsService;
  @MockitoBean private TimelineService timelineService;

  @Test
  void createReturnsCreatedWithPatientResponse() {
    when(patientService.createPatient(any())).thenReturn(PRIYA);

    assertThat(
            mvc.post()
                .uri("/api/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_PATIENT))
        .hasStatus(HttpStatus.CREATED)
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isLenientlyEqualTo(PRIYA_JSON);
    verify(patientService)
        .createPatient(
            new PatientRequest(
                "Priya",
                "Nair",
                LocalDate.of(1985, 3, 14),
                "female",
                "555-0100",
                "priya@example.com"));
  }

  @Test
  void createWithMissingRequiredFieldsReturnsBadRequest() {
    assertThat(
            mvc.post()
                .uri("/api/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Priya\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo(400);
    verifyNoInteractions(patientService);
  }

  @Test
  void getReturnsPatientById() {
    when(patientService.getPatient("p1")).thenReturn(PRIYA);

    assertThat(mvc.get().uri("/api/patients/p1"))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo(PRIYA_JSON);
  }

  @Test
  void searchPassesNameAndIsoBirthDate() {
    when(patientService.searchPatients("Nair", LocalDate.of(1985, 3, 14)))
        .thenReturn(new PageResponse<>(List.of(PRIYA), 1));

    assertThat(mvc.get().uri("/api/patients").param("name", "Nair").param("dob", "1985-03-14"))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo("{\"items\":[" + PRIYA_JSON + "],\"total\":1}");
  }

  @Test
  void searchWithoutFiltersPassesNulls() {
    when(patientService.searchPatients(null, null)).thenReturn(new PageResponse<>(List.of(), 0));

    assertThat(mvc.get().uri("/api/patients")).hasStatusOk();
    verify(patientService).searchPatients(null, null);
  }

  @Test
  void summaryRouteNoLongerExists() {
    assertThat(mvc.get().uri("/api/patients/p1/summary")).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void snapshotReturnsJsonFromSnapshotService() {
    when(snapshotService.getSnapshot("p1"))
        .thenReturn(
            new SnapshotResponse(
                List.of(Map.of("display", "Hypertension")), List.of(), null, List.of()));

    assertThat(mvc.get().uri("/api/patients/p1/snapshot"))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .extractingPath("$.activeConditions[0].display")
        .isEqualTo("Hypertension");
  }

  @Test
  void trendsForwardsTypeAndPeriod() {
    when(trendsService.getTrends("p1", "bp", "6m"))
        .thenReturn(new TrendsResponse("6m", "2024-01-01", "2024-06-30", Map.of()));

    assertThat(mvc.get().uri("/api/patients/p1/trends").param("type", "bp").param("period", "6m"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.period")
        .isEqualTo("6m");
  }

  @Test
  void timelineForwardsPagingAndTypeFilters() {
    TimelineEvent event =
        new TimelineEvent(
            "e1",
            "encounter",
            "2024-05-01",
            "Visit",
            null,
            "finished",
            "Encounter/e1",
            null,
            null,
            null,
            null,
            null,
            null);
    when(timelineService.getTimeline("p1", 5, LocalDate.of(2024, 6, 1), "encounter"))
        .thenReturn(List.of(event));

    assertThat(
            mvc.get()
                .uri("/api/patients/p1/timeline")
                .param("limit", "5")
                .param("before", "2024-06-01")
                .param("types", "encounter"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$[0].title")
        .isEqualTo("Visit");
  }

  @Test
  void timelineWithNonNumericLimitReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/patients/p1/timeline").param("limit", "ten"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(timelineService);
  }

  @Test
  void timelineWithMalformedBeforeDateReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/patients/p1/timeline").param("before", "not-a-date"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status":400,"message":"Invalid value for parameter 'before'"}
            """);
    verifyNoInteractions(timelineService);
  }

  @Test
  void getWithBlankIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/patients/{id}", " "))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status":400,"message":"id must not be blank"}
            """);
    verifyNoInteractions(patientService);
  }

  @Test
  void snapshotWithBlankIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/patients/{id}/snapshot", " ")).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(snapshotService);
  }
}
