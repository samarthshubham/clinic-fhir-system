package io.github.samarthshubham.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.config.SecurityConfig;
import io.github.samarthshubham.clinic.dto.GoalRequest;
import io.github.samarthshubham.clinic.dto.GoalResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.service.GoalService;
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

@WebMvcTest(GoalController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class GoalControllerTest {

  private static final GoalResponse BP_GOAL =
      new GoalResponse(
          "g1",
          "p1",
          "Lower BP",
          "active",
          "8480-6",
          "Systolic BP",
          130.0,
          "mm[Hg]",
          LocalDate.of(2024, 12, 31));

  private static final String BP_GOAL_JSON =
      """
      {"id":"g1","patientId":"p1","description":"Lower BP","status":"active",
       "targetMeasureCode":"8480-6","targetMeasureDisplay":"Systolic BP","targetValue":130.0,
       "targetUnit":"mm[Hg]","targetDate":"2024-12-31"}
      """;

  @Autowired private MockMvcTester mvc;

  @MockitoBean private GoalService goalService;

  @Test
  void createReturnsCreatedWithGoalResponse() {
    when(goalService.createGoal(any())).thenReturn(BP_GOAL);

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
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo(BP_GOAL_JSON);
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
    when(goalService.getGoalsForPatient("p1")).thenReturn(new PageResponse<>(List.of(BP_GOAL), 1));

    assertThat(mvc.get().uri("/api/goals").param("patientId", "p1"))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo("{\"items\":[" + BP_GOAL_JSON + "],\"total\":1}");
  }

  @Test
  void listWithoutPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/goals")).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(goalService);
  }
}
