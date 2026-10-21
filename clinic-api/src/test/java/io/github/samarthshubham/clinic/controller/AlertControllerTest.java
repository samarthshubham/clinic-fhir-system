package io.github.samarthshubham.clinic.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.clinic.exception.GlobalExceptionHandler;
import io.github.samarthshubham.clinic.service.HighBpAlertService;
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

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AlertController(highBpAlertService))
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
}
