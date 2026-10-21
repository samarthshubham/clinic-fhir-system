package io.github.shubhamforge.clinic.controller;

import static io.github.shubhamforge.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.shubhamforge.clinic.dto.GoalRequest;
import io.github.shubhamforge.clinic.service.GoalService;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Goal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(GoalController.class)
class GoalControllerTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private GoalService goalService;

  @Test
  void createReturnsCreatedWithFhirGoal() {
    when(goalService.createGoal(any()))
        .thenReturn(new Goal().setDescription(new CodeableConcept().setText("Lower BP")));

    assertThat(
            mvc.post()
                .uri("/api/goals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","description":"Lower BP","status":"active",
                     "targetMeasureCode":"8480-6","targetMeasureDisplay":"Systolic BP",
                     "targetValue":130.0,"targetUnit":"mm[Hg]","targetDate":"2024-12-31"}
                    """))
        .hasStatus(HttpStatus.CREATED)
        .hasContentType("application/fhir+json")
        .bodyJson()
        .extractingPath("$.description.text")
        .isEqualTo("Lower BP");
    verify(goalService)
        .createGoal(
            new GoalRequest(
                "p1",
                "Lower BP",
                "active",
                "8480-6",
                "Systolic BP",
                130.0,
                "mm[Hg]",
                LocalDate.of(2024, 12, 31)));
  }

  @Test
  void createWithoutDescriptionReturnsBadRequest() {
    assertThat(
            mvc.post()
                .uri("/api/goals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","status":"active"}
                    """))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("must not be blank");
    verifyNoInteractions(goalService);
  }

  @Test
  void listReturnsGoalsForPatient() {
    when(goalService.getGoalsForPatient("p1"))
        .thenReturn(bundleOf(new Goal().setDescription(new CodeableConcept().setText("Lower BP"))));

    assertThat(mvc.get().uri("/api/goals").param("patientId", "p1"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.entry[0].resource.description.text")
        .isEqualTo("Lower BP");
  }

  @Test
  void listWithoutPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/goals")).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(goalService);
  }
}
