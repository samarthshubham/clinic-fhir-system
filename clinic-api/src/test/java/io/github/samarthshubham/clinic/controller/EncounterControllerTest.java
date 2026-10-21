package io.github.samarthshubham.clinic.controller;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.dto.EncounterRequest;
import io.github.samarthshubham.clinic.service.EncounterService;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.EncounterStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(EncounterController.class)
class EncounterControllerTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private EncounterService encounterService;

  @Test
  void createReturnsCreatedWithFhirEncounter() {
    when(encounterService.createEncounter(any()))
        .thenReturn(new Encounter().setStatus(EncounterStatus.FINISHED));

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
        .hasContentType("application/fhir+json")
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("finished");
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
    Encounter encounter = new Encounter();
    encounter.setId("e1");
    when(encounterService.getEncounter("e1")).thenReturn(encounter);

    assertThat(mvc.get().uri("/api/encounters/e1"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.id")
        .isEqualTo("e1");
  }

  @Test
  void listReturnsEncountersForPatient() {
    when(encounterService.getEncountersForPatient("p1"))
        .thenReturn(bundleOf(new Encounter().setStatus(EncounterStatus.FINISHED)));

    assertThat(mvc.get().uri("/api/encounters").param("patientId", "p1"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.entry[0].resource.status")
        .isEqualTo("finished");
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
