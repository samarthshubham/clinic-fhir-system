package io.github.samarthshubham.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.config.SecurityConfig;
import io.github.samarthshubham.clinic.dto.MedicationRequest;
import io.github.samarthshubham.clinic.dto.MedicationResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.service.MedicationService;
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

@WebMvcTest(MedicationController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class MedicationControllerTest {

  private static final MedicationResponse LISINOPRIL =
      new MedicationResponse(
          "m1", "p1", "Lisinopril 10mg", "active", "Once daily", LocalDate.of(2024, 2, 1));

  private static final String LISINOPRIL_JSON =
      """
      {"id":"m1","patientId":"p1","medicationName":"Lisinopril 10mg","status":"active",
       "dosageText":"Once daily","startDate":"2024-02-01"}
      """;

  @Autowired private MockMvcTester mvc;

  @MockitoBean private MedicationService medicationService;

  @Test
  void createReturnsCreatedWithMedicationResponse() {
    when(medicationService.createMedication(any())).thenReturn(LISINOPRIL);

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
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo(LISINOPRIL_JSON);
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
        .thenReturn(new PageResponse<>(List.of(LISINOPRIL), 1));

    assertThat(mvc.get().uri("/api/medications").param("patientId", "p1"))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo("{\"items\":[" + LISINOPRIL_JSON + "],\"total\":1}");
  }

  @Test
  void listWithoutPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/medications")).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(medicationService);
  }
}
