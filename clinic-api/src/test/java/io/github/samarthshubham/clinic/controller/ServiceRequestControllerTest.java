package io.github.samarthshubham.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.config.SecurityConfig;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.dto.ServiceRequestRequest;
import io.github.samarthshubham.clinic.dto.ServiceRequestResponse;
import io.github.samarthshubham.clinic.security.Role;
import io.github.samarthshubham.clinic.service.ServiceRequestService;
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

@WebMvcTest(ServiceRequestController.class)
@Import(SecurityConfig.class)
@WithRole(Role.ADMIN)
class ServiceRequestControllerTest {

  private static final ServiceRequestResponse LIPID_PANEL =
      new ServiceRequestResponse(
          "sr1",
          "p1",
          "e1",
          "pr1",
          "Lipid panel",
          "laboratory",
          "active",
          "routine",
          LocalDate.of(2024, 5, 1));

  private static final String LIPID_PANEL_JSON =
      """
      {"id":"sr1","patientId":"p1","encounterId":"e1","practitionerId":"pr1",
       "code":"Lipid panel","category":"laboratory","status":"active","priority":"routine",
       "authoredOn":"2024-05-01"}
      """;

  @Autowired private MockMvcTester mvc;

  @MockitoBean private ServiceRequestService serviceRequestService;

  @Test
  void createReturnsCreatedWithServiceRequestResponse() {
    when(serviceRequestService.createServiceRequest(any())).thenReturn(LIPID_PANEL);

    assertThat(
            mvc.post()
                .uri("/api/service-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","encounterId":"e1","practitionerId":"pr1",
                     "code":"Lipid panel","category":"laboratory","status":"active",
                     "priority":"routine","authoredOn":"2024-05-01"}
                    """))
        .hasStatus(HttpStatus.CREATED)
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo(LIPID_PANEL_JSON);
    verify(serviceRequestService)
        .createServiceRequest(
            new ServiceRequestRequest(
                "p1",
                "e1",
                "pr1",
                "Lipid panel",
                "laboratory",
                "active",
                "routine",
                LocalDate.of(2024, 5, 1)));
  }

  @Test
  void createWithoutPriorityReturnsBadRequest() {
    assertThat(
            mvc.post()
                .uri("/api/service-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","code":"Lipid panel","category":"laboratory",
                     "status":"active","authoredOn":"2024-05-01"}
                    """))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("must not be blank");
    verifyNoInteractions(serviceRequestService);
  }

  @Test
  void getReturnsServiceRequestById() {
    when(serviceRequestService.getServiceRequest("sr1")).thenReturn(LIPID_PANEL);

    assertThat(mvc.get().uri("/api/service-requests/sr1"))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo(LIPID_PANEL_JSON);
  }

  @Test
  void listForwardsPatientIdAndStatusFilter() {
    when(serviceRequestService.getServiceRequestsForPatient("p1", "active"))
        .thenReturn(new PageResponse<>(List.of(LIPID_PANEL), 1));

    assertThat(
            mvc.get()
                .uri("/api/service-requests")
                .param("patientId", "p1")
                .param("status", "active"))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo("{\"items\":[" + LIPID_PANEL_JSON + "],\"total\":1}");
  }

  @Test
  void listWithoutStatusPassesNullFilter() {
    when(serviceRequestService.getServiceRequestsForPatient("p1", null))
        .thenReturn(new PageResponse<>(List.of(), 0));

    assertThat(mvc.get().uri("/api/service-requests").param("patientId", "p1"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.total")
        .isEqualTo(0);
  }

  @Test
  void listWithoutPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/service-requests").param("status", "active"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(serviceRequestService);
  }
}
