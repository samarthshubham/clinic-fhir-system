package io.github.samarthshubham.clinic.controller;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.dto.AppointmentRequest;
import io.github.samarthshubham.clinic.service.AppointmentService;
import java.time.LocalDateTime;
import org.hl7.fhir.r4.model.Appointment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(AppointmentController.class)
class AppointmentControllerTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private AppointmentService appointmentService;

  @Test
  void createReturnsCreatedWithFhirAppointment() {
    Appointment created = new Appointment();
    created.setId("a1");
    when(appointmentService.createAppointment(any())).thenReturn(created);

    assertThat(
            mvc.post()
                .uri("/api/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","practitionerId":"pr1","start":"2024-06-01T09:00:00",
                     "end":"2024-06-01T09:30:00","description":"Follow-up","status":"booked"}
                    """))
        .hasStatus(HttpStatus.CREATED)
        .hasContentType("application/fhir+json")
        .bodyJson()
        .extractingPath("$.resourceType")
        .isEqualTo("Appointment");
    verify(appointmentService)
        .createAppointment(
            new AppointmentRequest(
                "p1",
                "pr1",
                LocalDateTime.of(2024, 6, 1, 9, 0),
                LocalDateTime.of(2024, 6, 1, 9, 30),
                "Follow-up",
                "booked"));
  }

  @Test
  void createWithoutStartReturnsBadRequest() {
    assertThat(
            mvc.post()
                .uri("/api/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"p1","end":"2024-06-01T09:30:00","status":"booked"}
                    """))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("must not be null");
    verifyNoInteractions(appointmentService);
  }

  @Test
  void listReturnsAppointmentsForPatient() {
    when(appointmentService.getAppointmentsForPatient("p1"))
        .thenReturn(bundleOf(new Appointment().setDescription("Follow-up")));

    assertThat(mvc.get().uri("/api/appointments").param("patientId", "p1"))
        .hasStatusOk()
        .hasContentType("application/fhir+json")
        .bodyJson()
        .extractingPath("$.entry[0].resource.description")
        .isEqualTo("Follow-up");
  }

  @Test
  void listWithoutPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/appointments")).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(appointmentService);
  }
}
