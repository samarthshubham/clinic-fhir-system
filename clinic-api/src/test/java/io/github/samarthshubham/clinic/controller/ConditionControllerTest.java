package io.github.samarthshubham.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.config.SecurityConfig;
import io.github.samarthshubham.clinic.dto.ConditionRequest;
import io.github.samarthshubham.clinic.dto.ConditionResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.security.Role;
import io.github.samarthshubham.clinic.service.ConditionService;
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

@WebMvcTest(ConditionController.class)
@Import(SecurityConfig.class)
@WithRole(Role.ADMIN)
class ConditionControllerTest {

  private static final ConditionResponse HYPERTENSION =
      new ConditionResponse(
          "c1", "p1", "e1", "I10", "Hypertension", "active", LocalDate.of(2024, 1, 15));

  private static final String HYPERTENSION_JSON =
      """
      {"id":"c1","patientId":"p1","encounterId":"e1","code":"I10","display":"Hypertension",
       "clinicalStatus":"active","onsetDate":"2024-01-15"}
      """;

  @Autowired private MockMvcTester mvc;

  @MockitoBean private ConditionService conditionService;

  @Test
  void createReturnsCreatedWithConditionResponse() {
    when(conditionService.createCondition(any())).thenReturn(HYPERTENSION);

    assertThat(
            mvc.post()
                .uri("/api/conditions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","encounterId":"e1","code":"I10","display":"Hypertension",
                     "clinicalStatus":"active","onsetDate":"2024-01-15"}
                    """))
        .hasStatus(HttpStatus.CREATED)
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo(HYPERTENSION_JSON);
    verify(conditionService)
        .createCondition(
            new ConditionRequest(
                "p1", "e1", "I10", "Hypertension", "active", LocalDate.of(2024, 1, 15)));
  }

  @Test
  void createWithoutOnsetDateReturnsBadRequest() {
    assertThat(
            mvc.post()
                .uri("/api/conditions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","code":"I10","display":"Hypertension","clinicalStatus":"active"}
                    """))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("must not be null");
    verifyNoInteractions(conditionService);
  }

  @Test
  void getReturnsConditionById() {
    when(conditionService.getCondition("c1")).thenReturn(HYPERTENSION);

    assertThat(mvc.get().uri("/api/conditions/c1"))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo(HYPERTENSION_JSON);
  }

  @Test
  void getUnknownConditionReturnsNotFound() {
    when(conditionService.getCondition("missing"))
        .thenThrow(new ResourceNotFoundException("Condition", "missing"));

    assertThat(mvc.get().uri("/api/conditions/missing")).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void listReturnsConditionsForPatient() {
    when(conditionService.getConditionsForPatient("p1"))
        .thenReturn(new PageResponse<>(List.of(HYPERTENSION), 1));

    assertThat(mvc.get().uri("/api/conditions").param("patientId", "p1"))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo("{\"items\":[" + HYPERTENSION_JSON + "],\"total\":1}");
  }

  @Test
  void listWithoutPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/conditions")).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(conditionService);
  }
}
