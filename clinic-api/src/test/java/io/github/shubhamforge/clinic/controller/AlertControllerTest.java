package io.github.shubhamforge.clinic.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.shubhamforge.clinic.service.HighBpAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

  @Mock private HighBpAlertService highBpAlertService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new AlertController(highBpAlertService)).build();
  }

  @Test
  void emptySubscriptionNotificationTriggersAlertHandling() throws Exception {
    mockMvc.perform(post("/api/alerts/bp-subscription")).andExpect(status().isNoContent());

    verify(highBpAlertService).handleNotification();
  }
}
