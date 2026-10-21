package io.github.samarthshubham.clinic.controller;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.dto.CarePlanRequest;
import io.github.samarthshubham.clinic.service.CarePlanService;
import java.time.LocalDate;
import java.util.List;
import org.hl7.fhir.r4.model.CarePlan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(CarePlanController.class)
class CarePlanControllerTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private CarePlanService carePlanService;

  @Test
  void createReturnsCreatedWithFhirCarePlan() {
    when(carePlanService.createCarePlan(any())).thenReturn(new CarePlan().setTitle("BP control"));

    assertThat(
            mvc.post()
                .uri("/api/care-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","conditionIds":["c1"],"title":"BP control","status":"active",
                     "periodStart":"2024-06-01","goalIds":["g1","g2"]}
                    """))
        .hasStatus(HttpStatus.CREATED)
        .hasContentType("application/fhir+json")
        .bodyJson()
        .extractingPath("$.title")
        .isEqualTo("BP control");
    verify(carePlanService)
        .createCarePlan(
            new CarePlanRequest(
                "p1",
                List.of("c1"),
                "BP control",
                "active",
                LocalDate.of(2024, 6, 1),
                List.of("g1", "g2")));
  }

  @Test
  void createWithoutTitleReturnsBadRequest() {
    assertThat(
            mvc.post()
                .uri("/api/care-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","status":"active","periodStart":"2024-06-01"}
                    """))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("must not be blank");
    verifyNoInteractions(carePlanService);
  }

  @Test
  void listReturnsCarePlansForPatient() {
    when(carePlanService.getCarePlansForPatient("p1"))
        .thenReturn(bundleOf(new CarePlan().setTitle("BP control")));

    assertThat(mvc.get().uri("/api/care-plans").param("patientId", "p1"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.entry[0].resource.title")
        .isEqualTo("BP control");
  }

  @Test
  void listWithoutPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/care-plans")).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(carePlanService);
  }
}
