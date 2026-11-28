package io.github.samarthshubham.clinic.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ca.uhn.fhir.context.FhirContext;
import io.github.samarthshubham.clinic.exception.PasRequestException;
import io.github.samarthshubham.clinic.exception.PasSubmissionUnavailableException;
import io.github.samarthshubham.clinic.support.GoldenFhir;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PasSubmitClientTest {

  private static final String URL = "http://payer.test/fhir/Claim/$submit";
  private static final MediaType FHIR_JSON = MediaType.parseMediaType("application/fhir+json");
  private static final FhirContext FHIR = FhirContext.forR4Cached();
  private final PayerTokenClient tokens = mock(PayerTokenClient.class);
  private final RestClient.Builder builder = RestClient.builder().baseUrl("http://payer.test");
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final PasSubmitClient client = new PasSubmitClient(builder.build(), tokens, FHIR);
  private final Bundle request = bundle("pas/request-polysomnography.json");
  private final Bundle response = bundle("pas/response-pended.json");

  @BeforeEach
  void token() {
    when(tokens.accessToken()).thenReturn("payer-token");
    response.setIdentifier(request.getIdentifier().copy());
    decision().getRequest().setReference(request.getEntryFirstRep().getFullUrl());
  }

  @Test
  void sendsFhirBundleAsAnAuthenticatedPasOperation() {
    server
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer payer-token"))
        .andExpect(content().contentType(FHIR_JSON))
        .andExpect(content().json(FHIR.newJsonParser().encodeResourceToString(request)))
        .andRespond(withSuccess(FHIR.newJsonParser().encodeResourceToString(response), FHIR_JSON));

    assertThat(client.submit(request).getPreAuthRef()).isEqualTo("PHP-PA-A6041E2F");
    server.verify();
  }

  @Test
  void rejectsAnUncorrelatedReply() {
    response.getIdentifier().setValue("different");
    server
        .expect(requestTo(URL))
        .andRespond(withSuccess(FHIR.newJsonParser().encodeResourceToString(response), FHIR_JSON));
    assertThatThrownBy(() -> client.submit(request))
        .isInstanceOf(PasSubmissionUnavailableException.class);
    server.verify();
  }

  @Test
  void rejectsAFhirOperationOutcomeInsteadOfTreatingItAsADecision() {
    server
        .expect(requestTo(URL))
        .andRespond(withSuccess("{\"resourceType\":\"OperationOutcome\"}", FHIR_JSON));
    assertThatThrownBy(() -> client.submit(request))
        .isInstanceOf(PasSubmissionUnavailableException.class);
    server.verify();
  }

  @Test
  void reportsARejectedClaimWithoutExposingPayerPayload() {
    server
        .expect(requestTo(URL))
        .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body("internal diagnostics"));
    assertThatThrownBy(() -> client.submit(request))
        .isInstanceOf(PasRequestException.class)
        .hasMessageNotContaining("internal diagnostics");
    server.verify();
  }

  @Test
  void evictsAnExpiredTokenAndLeavesRetryToTheCaller() {
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
    assertThatThrownBy(() -> client.submit(request))
        .isInstanceOf(PasSubmissionUnavailableException.class);
    verify(tokens).evict();
    server.verify();
  }

  private ClaimResponse decision() {
    return (ClaimResponse) response.getEntryFirstRep().getResource();
  }

  private static Bundle bundle(String path) {
    return FHIR.newJsonParser().parseResource(Bundle.class, GoldenFhir.read(path));
  }
}
