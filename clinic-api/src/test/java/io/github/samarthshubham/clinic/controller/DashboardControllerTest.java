package io.github.samarthshubham.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.dto.DashboardResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.service.DashboardService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private DashboardService dashboardService;

  @Test
  void getReturnsDashboardAsPlainJson() {
    when(dashboardService.getDashboard("p1"))
        .thenReturn(
            new DashboardResponse(
                null,
                Map.of("organization", "Riverside Clinic"),
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                List.of("snapshot unavailable")));

    assertThat(mvc.get().uri("/api/dashboard/p1"))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"careTeam":{"organization":"Riverside Clinic"},"warnings":["snapshot unavailable"]}
            """);
  }

  @Test
  void getUnknownPatientReturnsNotFound() {
    when(dashboardService.getDashboard("missing"))
        .thenThrow(new ResourceNotFoundException("Patient", "missing"));

    assertThat(mvc.get().uri("/api/dashboard/missing"))
        .hasStatus(HttpStatus.NOT_FOUND)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("Patient with id 'missing' not found");
  }
}
