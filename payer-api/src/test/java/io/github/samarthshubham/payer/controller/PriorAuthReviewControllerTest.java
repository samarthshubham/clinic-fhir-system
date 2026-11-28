package io.github.samarthshubham.payer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.payer.config.SecurityConfig;
import io.github.samarthshubham.payer.dto.PriorAuthReviewResponse;
import io.github.samarthshubham.payer.exception.PriorAuthConflictException;
import io.github.samarthshubham.payer.security.Role;
import io.github.samarthshubham.payer.service.PriorAuthReviewService;
import io.github.samarthshubham.payer.support.WithRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(PriorAuthReviewController.class)
@Import(SecurityConfig.class)
class PriorAuthReviewControllerTest {

  @Autowired private MockMvcTester mvc;
  @MockitoBean private PriorAuthReviewService service;

  @Test
  @WithRole(Role.PAYER_ADMIN)
  void permitsReadingButNotDecision() {
    when(service.review("claim-1")).thenReturn(response(true));

    assertThat(mvc.get().uri("/api/prior-auths/claim-1"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.pended")
        .isEqualTo(true);
    assertThat(
            mvc.post()
                .uri("/api/prior-auths/claim-1/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}"))
        .hasStatus(HttpStatus.FORBIDDEN);
  }

  @Test
  @WithRole(Role.UM_REVIEWER)
  void returnsSavedDecision() {
    when(service.decide(eq("claim-1"), any(), any())).thenReturn(response(false));

    assertThat(
            mvc.post()
                .uri("/api/prior-auths/claim-1/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.pended")
        .isEqualTo(false);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"decision\":\"DENY\",\"reasonText\":\"Clinical rationale\"}",
        "{\"decision\":\"DENY\",\"reasonCode\":\"criteria\",\"reasonText\":\"   \"}",
        "{\"decision\":\"DENY\",\"reasonCode\":\"   \",\"reasonText\":\"Clinical rationale\"}"
      })
  @WithRole(Role.UM_REVIEWER)
  void refusesDenialWithoutCodeAndText(String json) {
    assertThat(
            mvc.post()
                .uri("/api/prior-auths/claim-1/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo(400);
    verifyNoInteractions(service);
  }

  @Test
  @WithRole(Role.UM_REVIEWER)
  void sendsConflictWhenDecisionIsStale() {
    when(service.decide(eq("claim-1"), any(), any())).thenThrow(new PriorAuthConflictException());
    assertThat(
            mvc.post()
                .uri("/api/prior-auths/claim-1/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"PEND\"}"))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo(409);
  }

  private static PriorAuthReviewResponse response(boolean pended) {
    return new PriorAuthReviewResponse(
        "claim-1", "PHP-PA-1", null, pended, List.of(), List.of(), List.of());
  }
}
