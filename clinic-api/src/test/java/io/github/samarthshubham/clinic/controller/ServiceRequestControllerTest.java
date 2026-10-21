package io.github.samarthshubham.clinic.controller;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.dto.ServiceRequestRequest;
import io.github.samarthshubham.clinic.service.ServiceRequestService;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ServiceRequestController.class)
class ServiceRequestControllerTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private ServiceRequestService serviceRequestService;

  @Test
  void createReturnsCreatedWithFhirServiceRequest() {
    when(serviceRequestService.createServiceRequest(any()))
        .thenReturn(new ServiceRequest().setStatus(ServiceRequestStatus.ACTIVE));

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
        .hasContentType("application/fhir+json")
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("active");
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
    ServiceRequest serviceRequest = new ServiceRequest();
    serviceRequest.setId("sr1");
    when(serviceRequestService.getServiceRequest("sr1")).thenReturn(serviceRequest);

    assertThat(mvc.get().uri("/api/service-requests/sr1"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.id")
        .isEqualTo("sr1");
  }

  @Test
  void listForwardsPatientIdAndStatusFilter() {
    when(serviceRequestService.getServiceRequestsForPatient("p1", "active"))
        .thenReturn(bundleOf(new ServiceRequest().setStatus(ServiceRequestStatus.ACTIVE)));

    assertThat(
            mvc.get()
                .uri("/api/service-requests")
                .param("patientId", "p1")
                .param("status", "active"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.entry[0].resource.status")
        .isEqualTo("active");
  }

  @Test
  void listWithoutStatusPassesNullFilter() {
    when(serviceRequestService.getServiceRequestsForPatient("p1", null))
        .thenReturn(bundleOf(new ServiceRequest().setStatus(ServiceRequestStatus.COMPLETED)));

    assertThat(mvc.get().uri("/api/service-requests").param("patientId", "p1"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.entry[0].resource.status")
        .isEqualTo("completed");
  }

  @Test
  void listWithoutPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/service-requests").param("status", "active"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(serviceRequestService);
  }
}
