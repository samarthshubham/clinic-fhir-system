package io.github.samarthshubham.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import io.github.samarthshubham.clinic.client.PayerApiException;
import io.github.samarthshubham.clinic.config.JwtConfig;
import io.github.samarthshubham.clinic.config.SecurityConfig;
import io.github.samarthshubham.clinic.dto.DocumentationAnswer;
import io.github.samarthshubham.clinic.dto.DocumentationCoding;
import io.github.samarthshubham.clinic.dto.DocumentationEnableWhen;
import io.github.samarthshubham.clinic.dto.DocumentationItem;
import io.github.samarthshubham.clinic.dto.DocumentationItemAnswers;
import io.github.samarthshubham.clinic.dto.DocumentationRequest;
import io.github.samarthshubham.clinic.dto.DocumentationResponse;
import io.github.samarthshubham.clinic.dto.DocumentationValue;
import io.github.samarthshubham.clinic.exception.DocumentationConflictException;
import io.github.samarthshubham.clinic.exception.DocumentationNotFoundException;
import io.github.samarthshubham.clinic.exception.DocumentationValidationException;
import io.github.samarthshubham.clinic.security.Role;
import io.github.samarthshubham.clinic.security.RolePermissions;
import io.github.samarthshubham.clinic.service.dtr.DtrService;
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

@WebMvcTest(DocumentationController.class)
@Import(SecurityConfig.class)
@WithRole(Role.ADMIN)
class DocumentationControllerTest {

  private static final String URI = "/api/service-requests/sr1/documentation";
  private static final DocumentationValue YES =
      new DocumentationValue(null, null, null, true, null, null, null);

  private static final DocumentationResponse FORM =
      new DocumentationResponse(
          "sr1",
          "qr1",
          "http://praxishealthplan.example/fhir/Questionnaire/mri-lumbar-spine",
          "MRI lumbar spine",
          "in-progress",
          List.of(
              new DocumentationItem(
                  "neurological-deficit",
                  "Deficit?",
                  "boolean",
                  true,
                  false,
                  false,
                  List.of(),
                  List.of(),
                  null,
                  List.of(new DocumentationAnswer(YES, true)),
                  List.of()),
              new DocumentationItem(
                  "deficit-type",
                  "Type",
                  "choice",
                  false,
                  false,
                  false,
                  List.of(
                      new DocumentationValue(
                          null,
                          null,
                          null,
                          null,
                          null,
                          null,
                          new DocumentationCoding("http://example.org", "motor", "Motor"))),
                  List.of(new DocumentationEnableWhen("neurological-deficit", "=", YES)),
                  "all",
                  List.of(),
                  List.of())));

  private static final String YES_JSON =
      """
      {"valueString":null,"valueInteger":null,"valueDecimal":null,"valueBoolean":true,
       "valueDate":null,"valueDateTime":null,"valueCoding":null}
      """;

  private static final String FORM_JSON =
      """
      {"serviceRequestId":"sr1","questionnaireResponseId":"qr1",
       "questionnaire":"http://praxishealthplan.example/fhir/Questionnaire/mri-lumbar-spine",
       "title":"MRI lumbar spine","status":"in-progress",
       "items":[
        {"linkId":"neurological-deficit","text":"Deficit?","type":"boolean","required":true,
         "repeats":false,"readOnly":false,"options":[],"enableWhen":[],"enableBehavior":null,
         "answers":[{"value":%1$s,"prefilled":true}],"items":[]},
        {"linkId":"deficit-type","text":"Type","type":"choice","required":false,
         "repeats":false,"readOnly":false,
         "options":[{"valueString":null,"valueInteger":null,"valueDecimal":null,
          "valueBoolean":null,"valueDate":null,"valueDateTime":null,
          "valueCoding":{"system":"http://example.org","code":"motor","display":"Motor"}}],
         "enableWhen":[{"question":"neurological-deficit","operator":"=","answer":%1$s}],
         "enableBehavior":"all","answers":[],"items":[]}]}
      """
          .formatted(YES_JSON);

  private static final String SAVE_BODY =
      """
      {"answers":[{"linkId":"neurological-deficit","values":[{"valueBoolean":true}]}]}
      """;

  private static final DocumentationRequest SAVE_REQUEST =
      new DocumentationRequest(
          List.of(new DocumentationItemAnswers("neurological-deficit", List.of(YES))));

  @Autowired private MockMvcTester mvc;

  @MockitoBean private DtrService dtrService;

  @Test
  void getReturnsTheForm() {
    when(dtrService.getDocumentation("sr1")).thenReturn(FORM);

    assertThat(mvc.get().uri(URI))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo(FORM_JSON);
  }

  @Test
  void putSavesTheAnswersAsTheSignedInPractitioner() {
    when(dtrService.saveDocumentation(eq("sr1"), any(), eq("pr1"))).thenReturn(FORM);

    assertThat(
            mvc.put()
                .uri(URI)
                .with(
                    jwt()
                        .jwt(token -> token.claim(JwtConfig.PRACTITIONER_ID_CLAIM, "pr1"))
                        .authorities(RolePermissions.authoritiesFor(List.of(Role.CLINICIAN))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SAVE_BODY))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(FORM_JSON);
    verify(dtrService).saveDocumentation("sr1", SAVE_REQUEST, "pr1");
  }

  @Test
  void completeAcceptsAnswers() {
    when(dtrService.completeDocumentation(eq("sr1"), any(), isNull())).thenReturn(FORM);

    assertThat(
            mvc.post()
                .uri(URI + "/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SAVE_BODY))
        .hasStatusOk();
    verify(dtrService).completeDocumentation("sr1", SAVE_REQUEST, null);
  }

  @Test
  void completeWorksWithoutABody() {
    when(dtrService.completeDocumentation("sr1", null, null)).thenReturn(FORM);

    assertThat(mvc.post().uri(URI + "/complete")).hasStatusOk();
  }

  @Test
  void putWithoutAnswersIsABadRequest() {
    assertThat(mvc.put().uri(URI).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("must not be null");
    verifyNoInteractions(dtrService);
  }

  @Test
  void anAnswerWithoutALinkIdIsABadRequest() {
    assertThat(
            mvc.put()
                .uri(URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[{\"values\":[]}]}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("must not be blank");
    verifyNoInteractions(dtrService);
  }

  @Test
  void missingRequiredItemsAreUnprocessable() {
    when(dtrService.completeDocumentation("sr1", null, null))
        .thenThrow(
            new DocumentationValidationException(
                "Required questions are not answered: symptom-onset-date"));

    assertThat(mvc.post().uri(URI + "/complete"))
        .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"status":422,"message":"Required questions are not answered: symptom-onset-date"}
            """);
  }

  @Test
  void aCompletedFormIsAConflict() {
    when(dtrService.saveDocumentation(eq("sr1"), any(), isNull()))
        .thenThrow(new DocumentationConflictException("already completed"));

    assertThat(mvc.put().uri(URI).contentType(MediaType.APPLICATION_JSON).content(SAVE_BODY))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .isStrictlyEqualTo("{\"status\":409,\"message\":\"already completed\"}");
  }

  @Test
  void anOrderWithoutDocumentationIsNotFound() {
    when(dtrService.getDocumentation("sr1"))
        .thenThrow(new DocumentationNotFoundException("no documentation"));

    assertThat(mvc.get().uri(URI))
        .hasStatus(HttpStatus.NOT_FOUND)
        .bodyJson()
        .isStrictlyEqualTo("{\"status\":404,\"message\":\"no documentation\"}");
  }

  @Test
  void anUnreachablePayerIsServiceUnavailableWithoutDetails() {
    when(dtrService.getDocumentation("sr1"))
        .thenThrow(new PayerApiException("payer-api answered with HTTP 500"));

    assertThat(mvc.get().uri(URI))
        .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"status":503,"message":"The payer could not be reached, so the documentation form is unavailable. Try again later."}
            """);
  }
}
