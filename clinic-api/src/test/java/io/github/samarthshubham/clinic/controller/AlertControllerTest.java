package io.github.samarthshubham.clinic.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.clinic.dto.AlertResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.exception.GlobalExceptionHandler;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.service.AlertService;
import io.github.samarthshubham.clinic.service.HighBpAlertService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

  private static final String WEBHOOK = "/api/alerts/bp-subscription";

  @Mock private HighBpAlertService highBpAlertService;
  @Mock private AlertService alertService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AlertController(highBpAlertService, alertService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void notificationWithTrustedSecretTriggersAlertHandling() throws Exception {
    when(highBpAlertService.isTrustedWebhookSecret("s3cret")).thenReturn(true);

    mockMvc
        .perform(post(WEBHOOK).header(HighBpAlertService.SECRET_HEADER, "s3cret"))
        .andExpect(status().isNoContent());

    verify(highBpAlertService).handleNotification();
  }

  @Test
  void notificationWithWrongSecretIsRejected() throws Exception {
    when(highBpAlertService.isTrustedWebhookSecret("wrong")).thenReturn(false);

    mockMvc
        .perform(post(WEBHOOK).header(HighBpAlertService.SECRET_HEADER, "wrong"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.message").value("Missing or invalid webhook secret"));

    verify(highBpAlertService, never()).handleNotification();
  }

  @Test
  void notificationWithoutSecretIsRejected() throws Exception {
    when(highBpAlertService.isTrustedWebhookSecret(null)).thenReturn(false);

    mockMvc.perform(post(WEBHOOK)).andExpect(status().isUnauthorized());

    verify(highBpAlertService, never()).handleNotification();
  }

  @Test
  void listsAlertsForTheRequestedStatus() throws Exception {
    when(alertService.getAlerts("active")).thenReturn(new PageResponse<>(List.of(ALERT), 1));

    mockMvc
        .perform(get("/api/alerts").param("status", "active"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value("f1"))
        .andExpect(jsonPath("$.items[0].patientId").value("p1"))
        .andExpect(jsonPath("$.items[0].observationId").value("obs-9"))
        .andExpect(jsonPath("$.items[0].status").value("active"));
  }

  @Test
  void rejectsAnUnknownStatusFilter() throws Exception {
    mockMvc.perform(get("/api/alerts").param("status", "open")).andExpect(status().isBadRequest());

    verifyNoInteractions(alertService);
  }

  @Test
  void acknowledgeReturnsTheClosedAlert() throws Exception {
    when(alertService.acknowledge("f1"))
        .thenReturn(
            new AlertResponse("f1", "p1", "obs-9", "Systolic BP above 180 mmHg", null, "inactive"));

    mockMvc
        .perform(post("/api/alerts/f1/acknowledge"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("inactive"));
  }

  @Test
  void acknowledgingAnUnknownAlertIsNotFound() throws Exception {
    when(alertService.acknowledge("nope")).thenThrow(new ResourceNotFoundException("Flag", "nope"));

    mockMvc.perform(post("/api/alerts/nope/acknowledge")).andExpect(status().isNotFound());
  }

  private static final AlertResponse ALERT =
      new AlertResponse("f1", "p1", "obs-9", "Systolic BP above 180 mmHg", null, "active");
}
