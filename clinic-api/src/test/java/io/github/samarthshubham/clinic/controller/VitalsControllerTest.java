package io.github.samarthshubham.clinic.controller;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static io.github.samarthshubham.clinic.support.FhirClientStub.quantityObservation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.dto.VitalsRequest;
import io.github.samarthshubham.clinic.service.VitalsService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(VitalsController.class)
class VitalsControllerTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private VitalsService vitalsService;

  @Test
  void recordReturnsCreatedWithObservationBundle() {
    when(vitalsService.recordVitals(any())).thenReturn(bundleOf(quantityObservation(142)));

    assertThat(
            mvc.post()
                .uri("/api/vitals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","encounterId":"e1","effectiveDate":"2024-05-01",
                     "systolicBp":142,"diastolicBp":91,"weightKg":78.5,"spo2Percent":97.0,
                     "heartRateBpm":72,"temperatureCelsius":36.8}
                    """))
        .hasStatus(HttpStatus.CREATED)
        .hasContentType("application/fhir+json")
        .bodyJson()
        .extractingPath("$.entry[0].resource.valueQuantity.value")
        .isEqualTo(142.0);
    verify(vitalsService)
        .recordVitals(
            new VitalsRequest("p1", "e1", LocalDate.of(2024, 5, 1), 142, 91, 78.5, 97.0, 72, 36.8));
  }

  @Test
  void recordWithoutEffectiveDateReturnsBadRequest() {
    assertThat(
            mvc.post()
                .uri("/api/vitals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","systolicBp":142}
                    """))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("must not be null");
    verifyNoInteractions(vitalsService);
  }

  @Test
  void getForwardsPatientIdAndType() {
    when(vitalsService.getVitals("p1", "weight")).thenReturn(bundleOf(quantityObservation(78.5)));

    assertThat(mvc.get().uri("/api/vitals").param("patientId", "p1").param("type", "weight"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.entry[0].resource.valueQuantity.value")
        .isEqualTo(78.5);
  }

  @Test
  void getWithoutTypeReturnsAllVitals() {
    when(vitalsService.getVitals("p1", null)).thenReturn(bundleOf(quantityObservation(97)));

    assertThat(mvc.get().uri("/api/vitals").param("patientId", "p1")).hasStatusOk();
    verify(vitalsService).getVitals("p1", null);
  }

  @Test
  void getWithoutPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/vitals").param("type", "weight"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(vitalsService);
  }
}
