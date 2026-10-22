package io.github.samarthshubham.clinic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.controller.AlertController;
import io.github.samarthshubham.clinic.controller.PatientController;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.dto.TokenResponse;
import io.github.samarthshubham.clinic.security.Role;
import io.github.samarthshubham.clinic.service.AlertService;
import io.github.samarthshubham.clinic.service.HighBpAlertService;
import io.github.samarthshubham.clinic.service.PatientService;
import io.github.samarthshubham.clinic.service.SnapshotService;
import io.github.samarthshubham.clinic.service.TimelineService;
import io.github.samarthshubham.clinic.service.TokenService;
import io.github.samarthshubham.clinic.service.TrendsService;
import io.github.samarthshubham.clinic.support.TestAuth;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest({PatientController.class, AlertController.class})
@Import(SecurityConfig.class)
class SecurityConfigTest {

  @Autowired private MockMvcTester mvc;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private SecretKey jwtSigningKey;
  @Autowired private AuthProperties authProperties;

  @MockitoBean private PatientService patientService;
  @MockitoBean private SnapshotService snapshotService;
  @MockitoBean private TrendsService trendsService;
  @MockitoBean private TimelineService timelineService;
  @MockitoBean private HighBpAlertService highBpAlertService;
  @MockitoBean private AlertService alertService;

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
  void validAccessTokenAuthenticatesTheRequest() {
    when(patientService.searchPatients(null, null)).thenReturn(new PageResponse<>(List.of(), 0));

    assertThat(mvc.get().uri("/api/patients").header("Authorization", bearer(Role.CLINICIAN)))
        .hasStatusOk();
  }

  @Test
  void refreshTokenUsedAsBearerIsRejected() {
    String refreshToken = tokens(Role.CLINICIAN).refreshToken();

    assertThat(mvc.get().uri("/api/patients").header("Authorization", "Bearer " + refreshToken))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status":401,"message":"Invalid or expired token"}
            """);
  }

  @Test
  void garbageBearerTokenIsRejected() {
    assertThat(mvc.get().uri("/api/patients").header("Authorization", "Bearer not.a.jwt"))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .hasHeader("WWW-Authenticate", "Bearer");
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

  private TokenResponse tokens(Role role) {
    return new TokenService(jwtEncoder, jwtSigningKey, authProperties)
        .issueTokens(TestAuth.user(role));
  }

  private String bearer(Role role) {
    return "Bearer " + tokens(role).accessToken();
  }
}
