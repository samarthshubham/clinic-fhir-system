package io.github.samarthshubham.clinic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.controller.AlertController;
import io.github.samarthshubham.clinic.controller.PatientController;
import io.github.samarthshubham.clinic.service.HighBpAlertService;
import io.github.samarthshubham.clinic.service.PatientService;
import io.github.samarthshubham.clinic.service.SnapshotService;
import io.github.samarthshubham.clinic.service.TimelineService;
import io.github.samarthshubham.clinic.service.TrendsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest({PatientController.class, AlertController.class})
@Import(SecurityConfig.class)
class SecurityConfigTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private PatientService patientService;
  @MockitoBean private SnapshotService snapshotService;
  @MockitoBean private TrendsService trendsService;
  @MockitoBean private TimelineService timelineService;
  @MockitoBean private HighBpAlertService highBpAlertService;

  @Test
  void apiRequestWithoutCredentialsReturnsJson401() {
    assertThat(mvc.get().uri("/api/patients"))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status":401,"message":"Authentication required"}
            """);
  }

  @Test
  void writeWithoutCredentialsReturns401NotCsrf403() {
    assertThat(
            mvc.post().uri("/api/patients").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void alertWebhookIsReachableWithoutJwt() {
    when(highBpAlertService.isTrustedWebhookSecret("s3cret")).thenReturn(true);

    assertThat(
            mvc.post()
                .uri("/api/alerts/bp-subscription")
                .header(HighBpAlertService.SECRET_HEADER, "s3cret"))
        .hasStatus(HttpStatus.NO_CONTENT);
    verify(highBpAlertService).handleNotification();
  }
}
