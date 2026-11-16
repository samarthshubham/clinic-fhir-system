package io.github.samarthshubham.clinic.client;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.samarthshubham.clinic.config.PayerApiConfig;
import io.github.samarthshubham.clinic.config.PayerApiProperties;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CrdClientTest {

  private static final String PAYER = "http://payer.test";
  private static final String TOKEN_JSON =
      "{\"access_token\":\"system-token\",\"token_type\":\"bearer\",\"expires_in\":300}";
  private static final PayerApiProperties PROPERTIES =
      new PayerApiProperties(
          PAYER, "clinic-api", "test-secret", "system/Coverage.r", Duration.ofSeconds(2));

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JsonNode golden = CrdFixtures.json("order-sign-request.json");
  private final Patient patient = CrdFixtures.patient(golden);
  private final Coverage coverage = CrdFixtures.coverage(golden);
  private final Practitioner practitioner = CrdFixtures.practitioner(golden);

  private MockRestServiceServer server;
  private CrdClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(PAYER);
    server = MockRestServiceServer.bindTo(builder).build();
    client = clientFor(builder.build());
  }

  private CrdClient clientFor(RestClient restClient) {
    return new CrdClient(
        restClient,
        new PayerTokenClient(restClient, PROPERTIES),
        FhirContext.forR4Cached(),
        objectMapper);
  }

  private void expectToken() {
    server
        .expect(once(), requestTo(PAYER + "/oauth/token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Basic Y2xpbmljLWFwaTp0ZXN0LXNlY3JldA=="))
        .andExpect(content().formDataContains(Map.of("grant_type", "client_credentials")))
        .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
  }

  @Test
  void orderSignReturnsTheCoverageInformationAndCardsForTheDraft() {
    expectToken();
    server
        .expect(once(), requestTo(PAYER + "/cds-services/order-sign-crd"))
        .andExpect(header("Authorization", "Bearer system-token"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andRespond(
            request ->
                withSuccess(answerFor(request), MediaType.APPLICATION_JSON)
                    .createResponse(request));

    CrdResult result = client.orderSign(CrdFixtures.mriOrder(), patient, coverage, practitioner);

    server.verify();
    assertThat(result.cards())
        .singleElement()
        .satisfies(
            card -> {
              assertThat(card.summary())
                  .isEqualTo("Prior authorization needed: MRI lumbar spine without contrast");
              assertThat(card.indicator()).isEqualTo("warning");
              assertThat(card.source()).isEqualTo("Praxis Health Plan");
            });
    Extension information = result.coverageInformation().getFirst();
    assertThat(information.getUrl()).isEqualTo(CrdClient.COVERAGE_INFORMATION);
    assertThat(information.getExtensionByUrl("pa-needed").getValue().primitiveValue())
        .isEqualTo("auth-needed");
    assertThat(((Reference) information.getExtensionByUrl("coverage").getValue()).getReference())
        .isEqualTo("Coverage/coverage-sandra-okafor");
  }

  @Test
  void theSystemTokenIsCachedAcrossHookCalls() {
    expectToken();
    server
        .expect(twice(), requestTo(PAYER + "/cds-services/order-sign-crd"))
        .andExpect(header("Authorization", "Bearer system-token"))
        .andRespond(withSuccess("{\"cards\":[]}", MediaType.APPLICATION_JSON));

    client.orderSign(CrdFixtures.mriOrder(), patient, coverage, practitioner);
    client.orderSign(CrdFixtures.mriOrder(), patient, coverage, practitioner);

    server.verify();
  }

  @Test
  void appointmentBookPostsTheDraftAppointmentToTheAppointmentBookService() {
    expectToken();
    server
        .expect(once(), requestTo(PAYER + "/cds-services/appointment-book-crd"))
        .andExpect(
            request -> {
              JsonNode body = body(request);
              assertThat(body.path("hook").asText()).isEqualTo("appointment-book");
              JsonNode appointment =
                  body.path("context").path("appointments").path("entry").path(0).path("resource");
              assertThat(appointment.path("resourceType").asText()).isEqualTo("Appointment");
              assertThat(appointment.path("status").asText()).isEqualTo("proposed");
              assertThat(appointment.path("meta").path("profile").path(0).asText())
                  .endsWith("profile-appointment-no-order");
              assertThat(body.path("prefetch").path("coverage").path("total").asInt()).isEqualTo(1);
            })
        .andRespond(withSuccess("{\"cards\":[]}", MediaType.APPLICATION_JSON));
    Appointment appointment = new Appointment();
    appointment.setStatus(Appointment.AppointmentStatus.BOOKED);
    appointment.addParticipant().setActor(new Reference("Patient/seed-sandra-okafor"));

    CrdResult result = client.appointmentBook(appointment, patient, coverage, null);

    server.verify();
    assertThat(result).isEqualTo(CrdResult.none());
  }

  @Test
  void aServerErrorFailsOpenWithACoverageUnknownCard() {
    expectToken();
    server
        .expect(once(), requestTo(PAYER + "/cds-services/order-sign-crd"))
        .andRespond(withServerError());

    assertThat(client.orderSign(CrdFixtures.mriOrder(), patient, coverage, practitioner))
        .isEqualTo(CrdResult.coverageUnknown());
  }

  @Test
  void aTimeoutFailsOpenWithACoverageUnknownCard() {
    expectToken();
    server
        .expect(once(), requestTo(PAYER + "/cds-services/order-sign-crd"))
        .andRespond(withException(new SocketTimeoutException("Read timed out")));

    assertThat(client.orderSign(CrdFixtures.mriOrder(), patient, coverage, practitioner))
        .isEqualTo(CrdResult.coverageUnknown());
  }

  @Test
  void aTokenFailureFailsOpenWithACoverageUnknownCard() {
    server
        .expect(once(), requestTo(PAYER + "/oauth/token"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThat(client.orderSign(CrdFixtures.mriOrder(), patient, coverage, practitioner))
        .isEqualTo(CrdResult.coverageUnknown());
    server.verify();
  }

  @Test
  void anUnauthorizedHookCallDropsTheCachedToken() {
    expectToken();
    server
        .expect(once(), requestTo(PAYER + "/cds-services/order-sign-crd"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
    expectToken();
    server
        .expect(once(), requestTo(PAYER + "/cds-services/order-sign-crd"))
        .andRespond(withSuccess("{\"cards\":[]}", MediaType.APPLICATION_JSON));

    assertThat(client.orderSign(CrdFixtures.mriOrder(), patient, coverage, practitioner))
        .isEqualTo(CrdResult.coverageUnknown());
    assertThat(client.orderSign(CrdFixtures.mriOrder(), patient, coverage, practitioner))
        .isEqualTo(CrdResult.none());
    server.verify();
  }

  @Test
  void aResponseThatIsNotCdsHooksJsonFailsOpen() {
    expectToken();
    server
        .expect(once(), requestTo(PAYER + "/cds-services/order-sign-crd"))
        .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

    assertThat(client.orderSign(CrdFixtures.mriOrder(), patient, coverage, practitioner))
        .isEqualTo(CrdResult.coverageUnknown());
  }

  @Test
  void aPayerSlowerThanTheTimeoutFailsOpenAfterTwoSeconds() throws IOException {
    HttpServer slowPayer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    slowPayer.setExecutor(Executors.newCachedThreadPool());
    slowPayer.createContext("/oauth/token", exchange -> respond(exchange, TOKEN_JSON));
    slowPayer.createContext(
        "/cds-services/order-sign-crd",
        exchange -> {
          try {
            Thread.sleep(6_000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          respond(exchange, "{\"cards\":[]}");
        });
    slowPayer.start();
    try {
      RestClient restClient =
          PayerApiConfig.payerApiRestClient(
              RestClient.builder(),
              "http://127.0.0.1:" + slowPayer.getAddress().getPort(),
              PROPERTIES.timeout());
      long started = System.nanoTime();

      CrdResult result =
          clientFor(restClient).orderSign(CrdFixtures.mriOrder(), patient, coverage, practitioner);

      Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
      assertThat(result).isEqualTo(CrdResult.coverageUnknown());
      assertThat(elapsed).isBetween(Duration.ofMillis(1_900), Duration.ofMillis(4_000));
    } finally {
      slowPayer.stop(0);
    }
  }

  private static void respond(HttpExchange exchange, String json) throws IOException {
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  private JsonNode body(ClientHttpRequest request) throws IOException {
    return objectMapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
  }

  private String answerFor(ClientHttpRequest request) throws IOException {
    String draftId =
        body(request)
            .path("context")
            .path("draftOrders")
            .path("entry")
            .path(0)
            .path("resource")
            .path("id")
            .asText();
    assertThat(draftId).startsWith("draft-");
    return CrdFixtures.read("order-sign-response-pa-needed.json")
        .replace("draft-mri-lumbar-spine", draftId);
  }

  @Test
  void parseIgnoresSystemActionsForOtherResources() throws IOException {
    JsonNode response =
        objectMapper.readTree(CrdFixtures.read("order-sign-response-pa-needed.json"));

    CrdResult result = client.parseResponse(response, "ServiceRequest/another-draft");

    assertThat(result.coverageInformation()).isEqualTo(List.of());
    assertThat(result.cards()).hasSize(1);
  }
}
