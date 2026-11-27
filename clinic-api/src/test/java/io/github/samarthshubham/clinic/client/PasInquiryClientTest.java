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
import io.github.samarthshubham.clinic.support.GoldenFhir;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PasInquiryClientTest {

  private static final String BASE = "http://payer.test";
  private static final FhirContext FHIR = FhirContext.forR4Cached();
  private static final MediaType FHIR_JSON = MediaType.parseMediaType("application/fhir+json");

  private final PayerTokenClient tokens = mock(PayerTokenClient.class);
  private final Bundle request =
      FHIR.newJsonParser().parseResource(Bundle.class, GoldenFhir.read("pas/inquire-request.json"));
  private MockRestServiceServer server;
  private PasInquiryClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    server = MockRestServiceServer.bindTo(builder).build();
    when(tokens.accessToken()).thenReturn("test-system-token");
    client = new PasInquiryClient(builder.build(), tokens, FHIR);
  }

  @Test
  void postsTheBundleAndParsesTheCurrentDecision() {
    server
        .expect(requestTo(BASE + "/fhir/Claim/$inquire"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer test-system-token"))
        .andExpect(content().contentTypeCompatibleWith(FHIR_JSON))
        .andExpect(content().string(FHIR.newJsonParser().encodeResourceToString(request)))
        .andRespond(withSuccess(GoldenFhir.read("pas/inquire-response.json"), FHIR_JSON));

    assertThat(client.inquire(request))
        .hasValueSatisfying(
            response -> assertThat(response.getPreAuthRef()).isEqualTo("PHP-PA-A6041E2F"));
    server.verify();
  }

  @Test
  void handlesNoMatch() {
    server
        .expect(requestTo(BASE + "/fhir/Claim/$inquire"))
        .andRespond(withSuccess("{\"resourceType\":\"Parameters\"}", FHIR_JSON));
    assertThat(client.inquire(request)).isEmpty();
    server.verify();
  }

  @Test
  void evictsAnUnauthorizedSystemTokenSoTheNextAttemptCanRefreshIt() {
    server
        .expect(requestTo(BASE + "/fhir/Claim/$inquire"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
    assertThatThrownBy(() -> client.inquire(request))
        .isInstanceOf(PayerApiException.class)
        .hasMessageContaining("HTTP 401");
    verify(tokens).evict();
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "invalid",
        "{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"return\",\"valueString\":\"unexpected\"}]}"
      })
  void rejectsUnusableResponses(String response) {
    server
        .expect(requestTo(BASE + "/fhir/Claim/$inquire"))
        .andRespond(withSuccess(response, FHIR_JSON));
    assertThatThrownBy(() -> client.inquire(request)).isInstanceOf(PayerApiException.class);
    server.verify();
  }
}
