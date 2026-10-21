package io.github.samarthshubham.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.dto.EncounterRequest;
import io.github.samarthshubham.clinic.dto.EncounterResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.service.EncounterService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(EncounterController.class)
class EncounterControllerTest {

  private static final EncounterResponse VISIT =
      new EncounterResponse(
          "e1", "p1", LocalDate.of(2024, 5, 1), "Headache", "finished", "pr1", "Rest advised");

  private static final String VISIT_JSON =
      """
      {"id":"e1","patientId":"p1","visitDate":"2024-05-01","reason":"Headache",
       "status":"finished","practitionerId":"pr1","note":"Rest advised"}
      """;

  @Autowired private MockMvcTester mvc;

  @MockitoBean private EncounterService encounterService;

  @Test
  void createReturnsCreatedWithEncounterResponse() {
    when(encounterService.createEncounter(any())).thenReturn(VISIT);

    assertThat(
            mvc.post()
                .uri("/api/encounters")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","visitDate":"2024-05-01","reason":"Headache",
                     "status":"finished","practitionerId":"pr1","note":"Rest advised"}
                    """))
        .hasStatus(HttpStatus.CREATED)
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo(VISIT_JSON);
    verify(encounterService)
        .createEncounter(
            new EncounterRequest(
                "p1", LocalDate.of(2024, 5, 1), "Headache", "finished", "pr1", "Rest advised"));
  }

  @Test
  void createWithBlankPatientIdReturnsBadRequest() {
    assertThat(
            mvc.post()
                .uri("/api/encounters")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":" ","visitDate":"2024-05-01","status":"finished"}
                    """))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("must not be blank");
    verifyNoInteractions(encounterService);
  }

  @Test
  void getReturnsEncounterById() {
    when(encounterService.getEncounter("e1")).thenReturn(VISIT);

    assertThat(mvc.get().uri("/api/encounters/e1"))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo(VISIT_JSON);
  }

  @Test
  void listReturnsEncountersForPatient() {
    when(encounterService.getEncountersForPatient("p1"))
        .thenReturn(new PageResponse<>(List.of(VISIT), 1));

    assertThat(mvc.get().uri("/api/encounters").param("patientId", "p1"))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo("{\"items\":[" + VISIT_JSON + "],\"total\":1}");
  }

  @Test
  void listWithoutPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/encounters")).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(encounterService);
  }

  @Test
  void listWithBlankPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/encounters").param("patientId", " "))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("patientId must not be blank");
    verifyNoInteractions(encounterService);
  }

  @Test
  void listWithoutPatientIdReportsTheMissingParameter() {
    assertThat(mvc.get().uri("/api/encounters"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("Missing required parameter 'patientId'");
  }

  @Test
  void getWithBlankIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/encounters/{id}", " ")).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(encounterService);
  }
}
