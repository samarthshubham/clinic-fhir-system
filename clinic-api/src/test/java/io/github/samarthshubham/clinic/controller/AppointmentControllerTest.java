package io.github.samarthshubham.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.config.SecurityConfig;
import io.github.samarthshubham.clinic.dto.AppointmentRequest;
import io.github.samarthshubham.clinic.dto.AppointmentResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.security.Role;
import io.github.samarthshubham.clinic.service.AppointmentService;
import io.github.samarthshubham.clinic.support.WithRole;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(AppointmentController.class)
@Import(SecurityConfig.class)
@WithRole(Role.ADMIN)
class AppointmentControllerTest {

  private static final AppointmentResponse FOLLOW_UP =
      new AppointmentResponse(
          "a1",
          "p1",
          "pr1",
          OffsetDateTime.parse("2024-06-01T09:00:00-04:00"),
          OffsetDateTime.parse("2024-06-01T09:30:00-04:00"),
          "Follow-up",
          "booked");

  private static final String FOLLOW_UP_JSON =
      """
      {"id":"a1","patientId":"p1","practitionerId":"pr1","start":"2024-06-01T09:00:00-04:00",
       "end":"2024-06-01T09:30:00-04:00","description":"Follow-up","status":"booked"}
      """;

  @Autowired private MockMvcTester mvc;

  @MockitoBean private AppointmentService appointmentService;

  @Test
  void createReturnsCreatedWithAppointmentResponse() {
    when(appointmentService.createAppointment(any())).thenReturn(FOLLOW_UP);

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
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo(FOLLOW_UP_JSON);
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
        .thenReturn(new PageResponse<>(List.of(FOLLOW_UP), 1));

    assertThat(mvc.get().uri("/api/appointments").param("patientId", "p1"))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo("{\"items\":[" + FOLLOW_UP_JSON + "],\"total\":1}");
  }

  @Test
  void listWithoutPatientIdReturnsBadRequest() {
    assertThat(mvc.get().uri("/api/appointments")).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(appointmentService);
  }
}
