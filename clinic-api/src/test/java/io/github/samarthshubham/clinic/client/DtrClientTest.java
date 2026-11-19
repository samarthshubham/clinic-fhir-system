package io.github.samarthshubham.clinic.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ca.uhn.fhir.context.FhirContext;
import io.github.samarthshubham.clinic.config.PayerApiProperties;
import io.github.samarthshubham.clinic.support.GoldenFhir;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DtrClientTest {

  private static final String PAYER = "http://payer.test";
  private static final String PACKAGE_URL = PAYER + DtrClient.QUESTIONNAIRE_PACKAGE;
  private static final String TOKEN_JSON =
      "{\"access_token\":\"system-token\",\"token_type\":\"bearer\",\"expires_in\":300}";
  private static final PayerApiProperties PROPERTIES =
      new PayerApiProperties(
          PAYER,
          "clinic-api",
          "test-secret",
          "system/Coverage.r system/Questionnaire.r",
          Duration.ofSeconds(2),
          "praxis-health-plan");
  private static final FhirContext FHIR = FhirContext.forR4Cached();

  private MockRestServiceServer server;
  private DtrClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(PAYER);
    server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();
    client = new DtrClient(restClient, new PayerTokenClient(restClient, PROPERTIES), FHIR);
  }

  @Test
  void returnsTheQuestionnaireResponseAndValueSetsOfTheFirstPackage() {
    expectToken();
    server
        .expect(once(), requestTo(PACKAGE_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer system-token"))
        .andExpect(content().contentTypeCompatibleWith(DtrClient.FHIR_JSON))
        .andRespond(
            withSuccess(
                GoldenFhir.read("dtr/questionnaire-package-output-cgm.json"), DtrClient.FHIR_JSON));

    Optional<DtrPackage> found = client.questionnairePackage(order(), coverage());

    server.verify();
    assertThat(found)
        .hasValueSatisfying(
            questionnairePackage -> {
              assertThat(questionnairePackage.questionnaire().getUrl())
                  .isEqualTo(
                      "http://praxishealthplan.example/fhir/Questionnaire/continuous-glucose-monitor");
              assertThat(questionnairePackage.response().getStatus().toCode())
                  .isEqualTo("in-progress");
              assertThat(questionnairePackage.valueSets())
                  .singleElement()
                  .satisfies(
                      valueSet ->
                          assertThat(valueSet.getUrl())
                              .isEqualTo(
                                  "http://praxishealthplan.example/fhir/ValueSet/diabetes-types"));
            });
  }

  @Test
  void sendsTheCoverageAndOrderWithTheirDaVinciProfilesAndNoClinicExtensions() throws IOException {
    expectToken();
    String[] sent = new String[1];
    server
        .expect(once(), requestTo(PACKAGE_URL))
        .andRespond(
            request -> {
              sent[0] = ((MockClientHttpRequest) request).getBodyAsString();
              return withSuccess(
                      FHIR.newJsonParser().encodeResourceToString(new Parameters()),
                      DtrClient.FHIR_JSON)
                  .createResponse(request);
            });

    Optional<DtrPackage> found = client.questionnairePackage(order(), coverage());

    assertThat(found).isEmpty();
    Parameters input = FHIR.newJsonParser().parseResource(Parameters.class, sent[0]);
    assertThat(input.getMeta().getProfile().getFirst().getValue())
        .isEqualTo(DtrClient.INPUT_PROFILE);
    Coverage coverage = (Coverage) input.getParameter("coverage").getResource();
    assertThat(coverage.getIdPart()).isEqualTo("cov1");
    assertThat(coverage.getMeta().getProfile().getFirst().getValue())
        .isEqualTo(CrdClient.CRD_PROFILES + "profile-coverage");
    ServiceRequest order = (ServiceRequest) input.getParameter("order").getResource();
    assertThat(order.getIdPart()).isEqualTo("sr1");
    assertThat(order.getMeta().getProfile().getFirst().getValue())
        .isEqualTo(CrdClient.CRD_PROFILES + "profile-servicerequest");
    assertThat(order.getExtension())
        .extracting(e -> e.getUrl())
        .containsExactly(CrdClient.COVERAGE_INFORMATION);
  }

  @Test
  void aPayerErrorIsAPayerApiException() {
    expectToken();
    server.expect(once(), requestTo(PACKAGE_URL)).andRespond(withServerError());

    assertThatThrownBy(() -> client.questionnairePackage(order(), coverage()))
        .isInstanceOf(PayerApiException.class)
        .hasMessageContaining("HTTP 500");
  }

  @Test
  void aTimeoutIsAPayerApiException() {
    expectToken();
    server
        .expect(once(), requestTo(PACKAGE_URL))
        .andRespond(withException(new SocketTimeoutException("Read timed out")));

    assertThatThrownBy(() -> client.questionnairePackage(order(), coverage()))
        .isInstanceOf(PayerApiException.class)
        .hasMessage("payer-api could not be reached");
  }

  @Test
  void aRejectedTokenIsEvictedSoTheNextCallAsksForANewOne() {
    server
        .expect(twice(), requestTo(PAYER + "/oauth/token"))
        .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
    server.expect(once(), requestTo(PACKAGE_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
    server
        .expect(once(), requestTo(PACKAGE_URL))
        .andRespond(
            withSuccess(
                FHIR.newJsonParser().encodeResourceToString(new Parameters()),
                DtrClient.FHIR_JSON));

    assertThatThrownBy(() -> client.questionnairePackage(order(), coverage()))
        .isInstanceOf(PayerApiException.class);
    assertThat(client.questionnairePackage(order(), coverage())).isEmpty();
    server.verify();
  }

  @Test
  void aBodyThatIsNotParametersIsAPayerApiException() {
    expectToken();
    server
        .expect(once(), requestTo(PACKAGE_URL))
        .andRespond(withSuccess("{\"resourceType\":\"Nope\"}", DtrClient.FHIR_JSON));

    assertThatThrownBy(() -> client.questionnairePackage(order(), coverage()))
        .isInstanceOf(PayerApiException.class)
        .hasMessageContaining("invalid FHIR");
  }

  @Test
  void anEmptyBodyIsAPayerApiException() {
    expectToken();
    server.expect(once(), requestTo(PACKAGE_URL)).andRespond(withSuccess());

    assertThatThrownBy(() -> client.questionnairePackage(order(), coverage()))
        .isInstanceOf(PayerApiException.class)
        .hasMessageContaining("no body");
  }

  @Test
  void aPackageWithoutAQuestionnaireIsAPayerApiException() {
    expectToken();
    Parameters output = new Parameters();
    output.addParameter().setName("outcome").setValue(new StringType("ignored"));
    Bundle withoutQuestionnaire = new Bundle().setType(Bundle.BundleType.COLLECTION);
    withoutQuestionnaire.addEntry().setResource(new ValueSet().setUrl("http://example.org/vs"));
    output.addParameter().setName("packagebundle").setResource(withoutQuestionnaire);
    server
        .expect(once(), requestTo(PACKAGE_URL))
        .andRespond(
            withSuccess(FHIR.newJsonParser().encodeResourceToString(output), DtrClient.FHIR_JSON));

    assertThatThrownBy(() -> client.questionnairePackage(order(), coverage()))
        .isInstanceOf(PayerApiException.class)
        .hasMessageContaining("without a Questionnaire");
  }

  private void expectToken() {
    server
        .expect(once(), requestTo(PAYER + "/oauth/token"))
        .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
  }

  private static ServiceRequest order() {
    ServiceRequest order = new ServiceRequest();
    order.setId("http://hapi.test/fhir/ServiceRequest/sr1/_history/2");
    order.setSubject(new Reference("Patient/p1"));
    order
        .addExtension()
        .setUrl(CrdClient.COVERAGE_INFORMATION)
        .addExtension("covered", new CodeType("covered"));
    order.addExtension(CrdClient.CLINIC_FHIR_BASE + "/extension/crd-card", new StringType("card"));
    return order;
  }

  private static Coverage coverage() {
    Coverage coverage = new Coverage();
    coverage.setId("http://hapi.test/fhir/Coverage/cov1/_history/1");
    coverage.setBeneficiary(new Reference("Patient/p1"));
    return coverage;
  }
}
