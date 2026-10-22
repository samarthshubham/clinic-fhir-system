package io.github.samarthshubham.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.config.SecurityConfig;
import io.github.samarthshubham.clinic.dto.ObservationResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.dto.VitalsRequest;
import io.github.samarthshubham.clinic.security.Role;
import io.github.samarthshubham.clinic.service.VitalsService;
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

@WebMvcTest(VitalsController.class)
@Import(SecurityConfig.class)
@WithRole(Role.ADMIN)
class VitalsControllerTest {

  private static final ObservationResponse SYSTOLIC =
      new ObservationResponse(
          "o1", "p1", "e1", "systolic", "8480-6", 142.0, "mm[Hg]", "2024-05-01T00:00:00-04:00");

  private static final String SYSTOLIC_JSON =
      """
      {"id":"o1","patientId":"p1","encounterId":"e1","type":"systolic","loincCode":"8480-6",
       "value":142.0,"unit":"mm[Hg]","effectiveDateTime":"2024-05-01T00:00:00-04:00"}
      """;

  @Autowired private MockMvcTester mvc;

  @MockitoBean private VitalsService vitalsService;

  @Test
  void recordReturnsCreatedWithObservationList() {
    when(vitalsService.recordVitals(any(), eq(false))).thenReturn(List.of(SYSTOLIC));

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
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo("[" + SYSTOLIC_JSON + "]");
    verify(vitalsService)
        .recordVitals(
            new VitalsRequest("p1", "e1", LocalDate.of(2024, 5, 1), 142, 91, 78.5, 97.0, 72, 36.8),
            false);
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
    when(vitalsService.getVitals("p1", "systolic"))
        .thenReturn(new PageResponse<>(List.of(SYSTOLIC), 1));

    assertThat(mvc.get().uri("/api/vitals").param("patientId", "p1").param("type", "systolic"))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo("{\"items\":[" + SYSTOLIC_JSON + "],\"total\":1}");
  }

  @Test
  void getWithoutTypeReturnsAllVitals() {
    when(vitalsService.getVitals("p1", null)).thenReturn(new PageResponse<>(List.of(), 0));

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
