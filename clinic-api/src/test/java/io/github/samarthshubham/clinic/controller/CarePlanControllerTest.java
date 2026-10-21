package io.github.samarthshubham.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.dto.CarePlanRequest;
import io.github.samarthshubham.clinic.dto.CarePlanResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.service.CarePlanService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(CarePlanController.class)
class CarePlanControllerTest {

  private static final CarePlanResponse BP_PLAN =
      new CarePlanResponse(
          "cp1",
          "p1",
          List.of("c1"),
          "BP control",
          "active",
          LocalDate.of(2024, 6, 1),
          List.of("g1", "g2"));

  private static final String BP_PLAN_JSON =
      """
      {"id":"cp1","patientId":"p1","conditionIds":["c1"],"title":"BP control",
       "status":"active","periodStart":"2024-06-01","goalIds":["g1","g2"]}
      """;

  @Autowired private MockMvcTester mvc;

  @MockitoBean private CarePlanService carePlanService;

  @Test
  void createReturnsCreatedWithCarePlanResponse() {
    when(carePlanService.createCarePlan(any())).thenReturn(BP_PLAN);

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
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo(BP_PLAN_JSON);
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
        .thenReturn(new PageResponse<>(List.of(BP_PLAN), 1));

    assertThat(mvc.get().uri("/api/care-plans").param("patientId", "p1"))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo("{\"items\":[" + BP_PLAN_JSON + "],\"total\":1}");
  }

  @Test
  void listWithoutPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/care-plans")).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(carePlanService);
  }
}
