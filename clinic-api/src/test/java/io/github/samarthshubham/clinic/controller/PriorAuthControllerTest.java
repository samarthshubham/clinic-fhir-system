package io.github.samarthshubham.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.config.SecurityConfig;
import io.github.samarthshubham.clinic.dto.PriorAuthResponse;
import io.github.samarthshubham.clinic.dto.PriorAuthResponse.Status;
import io.github.samarthshubham.clinic.exception.PasSubmissionUnavailableException;
import io.github.samarthshubham.clinic.security.Role;
import io.github.samarthshubham.clinic.service.pas.PriorAuthService;
import io.github.samarthshubham.clinic.support.WithRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(PriorAuthController.class)
@Import(SecurityConfig.class)
@WithRole(Role.CLINICIAN)
class PriorAuthControllerTest {

  private static final String PATH = "/api/prior-auths";
  private static final String REQUEST =
      """
      {"orderId":"order-1","placeOfService":"11","serviceCategory":"1"}
      """;
  @Autowired private MockMvcTester mvc;
  @MockitoBean private PriorAuthService priorAuths;

  @Test
  void submitsAndReturnsTheOrderDecision() {
    when(priorAuths.submit(any()))
        .thenReturn(new PriorAuthResponse("order-1", Status.APPROVED, "PA-1", null, null));
    assertThat(mvc.post().uri(PATH).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.authorizationNumber")
        .isEqualTo("PA-1");
  }

  @Test
  void listsAuthorizationStatusForTheRequestedPatient() {
    when(priorAuths.list("patient-1"))
        .thenReturn(
            List.of(
                new PriorAuthResponse(
                    "order-1", Status.DENIED, null, "reason", "Clinical criteria not met")));
    assertThat(mvc.get().uri(PATH).param("patientId", "patient-1"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$[0].denialReason")
        .isEqualTo("Clinical criteria not met");
  }

  @Test
  void validatesOrderAndBillingBeforeCallingTheService() {
    assertThat(
            mvc.post()
                .uri(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"orderId\":\"Patient/p1\",\"placeOfService\":\"x\",\"serviceCategory\":\"1\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(priorAuths);
  }

  @Test
  void reportsAnUnavailablePayerWithTheMatchingAction() {
    when(priorAuths.submit(any())).thenThrow(new PasSubmissionUnavailableException("Try again."));
    assertThat(mvc.post().uri(PATH).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
        .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("Try again.");
  }
}
