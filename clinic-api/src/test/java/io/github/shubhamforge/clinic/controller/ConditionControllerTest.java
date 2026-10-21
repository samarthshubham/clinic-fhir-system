package io.github.shubhamforge.clinic.controller;

import static io.github.shubhamforge.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.shubhamforge.clinic.dto.ConditionRequest;
import io.github.shubhamforge.clinic.exception.ResourceNotFoundException;
import io.github.shubhamforge.clinic.service.ConditionService;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ConditionController.class)
class ConditionControllerTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private ConditionService conditionService;

  @Test
  void createReturnsCreatedWithFhirCondition() {
    Condition created = new Condition();
    created.setId("c1");
    when(conditionService.createCondition(any())).thenReturn(created);

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
        .hasContentType("application/fhir+json")
        .bodyJson()
        .extractingPath("$.id")
        .isEqualTo("c1");
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
    Condition condition = new Condition();
    condition.setId("c1");
    when(conditionService.getCondition("c1")).thenReturn(condition);

    assertThat(mvc.get().uri("/api/conditions/c1"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.id")
        .isEqualTo("c1");
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
        .thenReturn(
            bundleOf(new Condition().setCode(new CodeableConcept().setText("Hypertension"))));

    assertThat(mvc.get().uri("/api/conditions").param("patientId", "p1"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.entry[0].resource.code.text")
        .isEqualTo("Hypertension");
  }

  @Test
  void listWithoutPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/conditions")).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(conditionService);
  }
}
