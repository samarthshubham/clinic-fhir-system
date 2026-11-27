package io.github.samarthshubham.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.config.SecurityConfig;
import io.github.samarthshubham.clinic.service.pas.PasDecisionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(PasDecisionWebhookController.class)
@Import(SecurityConfig.class)
class PasDecisionWebhookControllerTest {

  private static final String PATH = "/api/alerts/prior-auth-subscription";

  @Autowired private MockMvcTester mvc;
  @MockitoBean private PasDecisionService decisions;

  @Test
  void acceptsTheRestHookWithoutABearerToken() {
    when(decisions.isTrustedWebhookSecret("shared-test-secret")).thenReturn(true);

    assertThat(mvc.post().uri(PATH).header(PasDecisionService.SECRET_HEADER, "shared-test-secret"))
        .hasStatus(HttpStatus.NO_CONTENT);

    verify(decisions).refreshPending();
  }

  @Test
  void wrongSecretIsUnauthorized() {
    assertThat(mvc.post().uri(PATH).header(PasDecisionService.SECRET_HEADER, "wrong-secret"))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo(401);
    verify(decisions).isTrustedWebhookSecret("wrong-secret");
    org.mockito.Mockito.verifyNoMoreInteractions(decisions);
  }

  @Test
  void onlyPostCanReachTheWebhook() {
    assertThat(mvc.get().uri(PATH)).hasStatus(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(decisions);
  }
}
