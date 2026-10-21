package io.github.samarthshubham.clinic.controller;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.dto.MedicationRequest;
import io.github.samarthshubham.clinic.service.MedicationService;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.MedicationStatement.MedicationStatementStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(MedicationController.class)
class MedicationControllerTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private MedicationService medicationService;

  @Test
  void createReturnsCreatedWithFhirMedicationStatement() {
    when(medicationService.createMedication(any()))
        .thenReturn(new MedicationStatement().setStatus(MedicationStatementStatus.ACTIVE));

    assertThat(
            mvc.post()
                .uri("/api/medications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","medicationName":"Lisinopril 10mg","status":"active",
                     "dosageText":"Once daily","startDate":"2024-02-01"}
                    """))
        .hasStatus(HttpStatus.CREATED)
        .hasContentType("application/fhir+json")
        .bodyJson()
        .extractingPath("$.resourceType")
        .isEqualTo("MedicationStatement");
    verify(medicationService)
        .createMedication(
            new MedicationRequest(
                "p1", "Lisinopril 10mg", "active", "Once daily", LocalDate.of(2024, 2, 1)));
  }

  @Test
  void createWithMalformedDateReturnsBadRequest() {
    assertThat(
            mvc.post()
                .uri("/api/medications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","medicationName":"Lisinopril","status":"active",
                     "startDate":"yesterday"}
                    """))
        .hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(medicationService);
  }

  @Test
  void createWithoutMedicationNameReturnsBadRequest() {
    assertThat(
            mvc.post()
                .uri("/api/medications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","status":"active","startDate":"2024-02-01"}
                    """))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("must not be blank");
    verifyNoInteractions(medicationService);
  }

  @Test
  void listReturnsMedicationsForPatient() {
    when(medicationService.getMedicationsForPatient("p1"))
        .thenReturn(
            bundleOf(new MedicationStatement().setStatus(MedicationStatementStatus.ACTIVE)));

    assertThat(mvc.get().uri("/api/medications").param("patientId", "p1"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.entry[0].resource.status")
        .isEqualTo("active");
  }

  @Test
  void listWithoutPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/medications")).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(medicationService);
  }
}
