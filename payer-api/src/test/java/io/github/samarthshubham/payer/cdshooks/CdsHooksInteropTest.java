package io.github.samarthshubham.payer.cdshooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import io.github.samarthshubham.payer.service.TokenService;
import io.github.samarthshubham.payer.support.FhirClientStub;
import io.github.samarthshubham.payer.support.MemberFixtures;
import io.github.samarthshubham.payer.support.TestAuth;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Coverage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CdsHooksInteropTest.PayerHapi.class)
class CdsHooksInteropTest {

  static final String FIXTURES = "/fixtures/davinci/crd/";
  static final Instant NOW = Instant.parse("2026-11-11T09:30:00Z");

  private static final FhirClientStub PAYER_HAPI = new FhirClientStub();
  private static final AtomicReference<String> FALLBACK_AUTHORIZATION = new AtomicReference<>();
  private static final AtomicReference<String> FALLBACK_QUERY = new AtomicReference<>();
  private static final HttpServer EHR_FHIR_SERVER = startEhrFhirServer();

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @LocalServerPort private int port;

  @Autowired private TokenService tokenService;
  @Autowired private MeterRegistry meterRegistry;

  @TestConfiguration
  static class PayerHapi {

    @Bean
    @Primary
    IGenericClient stubPayerHapiClient() {
      return PAYER_HAPI.client();
    }

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @DynamicPropertySource
  static void allowTheTestEhrFhirServer(DynamicPropertyRegistry registry) {
    registry.add("payer.crd.prefetch-fhir-servers[0]", CdsHooksInteropTest::ehrFhirServerBase);
  }

  @AfterAll
  static void stopEhrFhirServer() {
    EHR_FHIR_SERVER.stop(0);
  }

  @Test
  void discoveryIsPublicAndListsBothServicesWithPrefetchTemplates() throws Exception {
    HttpResponse<String> response = get("/cds-services");

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode services = objectMapper.readTree(response.body()).path("services");
    assertThat(services).hasSize(2);
    assertThat(services.findValuesAsText("id"))
        .containsExactly("order-sign-crd", "order-select-crd");
    assertThat(services.findValuesAsText("hook")).containsExactly("order-sign", "order-select");
    for (JsonNode service : services) {
      JsonNode prefetch = service.path("prefetch");
      assertThat(prefetch.path("patient").asText()).isEqualTo("Patient/{{context.patientId}}");
      assertThat(prefetch.path("coverage").asText())
          .isEqualTo("Coverage?patient={{context.patientId}}&status=active");
      assertThat(prefetch.path("practitioner").asText()).isEqualTo("{{context.userId}}");
      assertThat(service.path("extension").path("davinci-crd.version").get(0).asText())
          .isEqualTo("2.2");
    }
  }

  @Test
  void orderSignForAnMriAnswersTheGoldenPaNeededResponse() throws Exception {
    givenPayerCoverage("PHP-10005", Coverage.CoverageStatus.ACTIVE, "2025-11-11", null);

    HttpResponse<String> response =
        post("/cds-services/order-sign-crd", fixture("order-sign-request.json"), clinicApi());

    assertMatchesGolden(response, "order-sign-response-pa-needed.json");
  }

  @Test
  void orderSignForACbcAnswersTheGoldenCoveredResponse() throws Exception {
    givenPayerCoverage("PHP-10001", Coverage.CoverageStatus.ACTIVE, "2025-11-11", null);

    HttpResponse<String> response =
        post(
            "/cds-services/order-sign-crd",
            fixture("order-sign-request-covered.json"),
            clinicApi());

    assertMatchesGolden(response, "order-sign-response-covered.json");
  }

  @Test
  void orderSelectGivesPaNeededWithoutDocumentationOrQuestionnaire() throws Exception {
    givenPayerCoverage("PHP-10005", Coverage.CoverageStatus.ACTIVE, "2025-11-11", null);
    ObjectNode request = goldenRequest();
    request.put("hook", "order-select");
    ((ObjectNode) request.path("context"))
        .putArray("selections")
        .add("ServiceRequest/draft-mri-lumbar-spine");

    JsonNode response = okJson(post("/cds-services/order-select-crd", request, clinicApi()));

    JsonNode information = coverageInformation(response);
    assertThat(subExtension(information, "pa-needed").path("valueCode").asText())
        .isEqualTo("auth-needed");
    assertThat(information.findValuesAsText("url"))
        .doesNotContain("doc-needed", "doc-purpose", "questionnaire");
    assertThat(response.path("cards").get(0).path("summary").asText())
        .isEqualTo("Prior authorization needed: MRI lumbar spine without contrast");
  }

  @Test
  void orderSelectOnlyAnswersForTheSelectedOrders() throws Exception {
    givenPayerCoverage("PHP-10005", Coverage.CoverageStatus.ACTIVE, "2025-11-11", null);
    ObjectNode request = goldenRequest();
    request.put("hook", "order-select");
    ((ObjectNode) request.path("context")).putArray("selections").add("ServiceRequest/other");

    JsonNode response = okJson(post("/cds-services/order-select-crd", request, clinicApi()));

    assertThat(response.path("cards")).isEmpty();
    assertThat(response.path("systemActions")).isEmpty();
  }

  @Test
  void cancelledPayerCoverageGivesACouldNotVerifyCard() throws Exception {
    givenPayerCoverage("PHP-10005", Coverage.CoverageStatus.CANCELLED, "2025-10-07", "2026-10-12");

    JsonNode response = okJson(post("/cds-services/order-sign-crd", goldenRequest(), clinicApi()));

    JsonNode card = response.path("cards").get(0);
    assertThat(card.path("summary").asText()).isEqualTo("Coverage could not be verified");
    assertThat(card.path("indicator").asText()).isEqualTo("warning");
    JsonNode information = coverageInformation(response);
    assertThat(subExtension(information, "covered").path("valueCode").asText())
        .isEqualTo("not-covered");
    assertThat(
            subExtension(information, "reason").at("/valueCodeableConcept/coding/0/code").asText())
        .isEqualTo("no-active-coverage");
    assertThat(information.findValuesAsText("url")).doesNotContain("pa-needed");
  }

  @Test
  void unknownMemberGivesNoMemberFound() throws Exception {
    PAYER_HAPI.givenSearchResult(Coverage.class, FhirClientStub.bundleOf());

    JsonNode response = okJson(post("/cds-services/order-sign-crd", goldenRequest(), clinicApi()));

    assertThat(
            subExtension(coverageInformation(response), "reason")
                .at("/valueCodeableConcept/coding/0/code")
                .asText())
        .isEqualTo("no-member-found");
  }

  @Test
  void missingPrefetchIsFetchedFromAnAllowedFhirServerWithTheHookToken() throws Exception {
    givenPayerCoverage("PHP-10005", Coverage.CoverageStatus.ACTIVE, "2025-11-11", null);
    ObjectNode request = goldenRequest();
    ((ObjectNode) request.path("prefetch")).remove("coverage");
    request.put("fhirServer", ehrFhirServerBase());
    request
        .putObject("fhirAuthorization")
        .put("access_token", "ehr-token")
        .put("token_type", "Bearer");

    JsonNode response = okJson(post("/cds-services/order-sign-crd", request, clinicApi()));

    assertThat(FALLBACK_AUTHORIZATION.get()).isEqualTo("Bearer ehr-token");
    assertThat(FALLBACK_QUERY.get()).isEqualTo("patient=seed-sandra-okafor&status=active");
    assertThat(
            subExtension(coverageInformation(response), "coverage")
                .at("/valueReference/reference")
                .asText())
        .isEqualTo("Coverage/fetched-coverage");
  }

  @Test
  void missingPrefetchWithAFhirServerOffTheAllowListIs412() throws Exception {
    ObjectNode request = goldenRequest();
    ((ObjectNode) request.path("prefetch")).remove("coverage");
    request.put("fhirServer", "http://169.254.169.254/fhir");

    assertOperationOutcome(post("/cds-services/order-sign-crd", request, clinicApi()), 412);
  }

  @Test
  void requestWithoutCoverageIs400() throws Exception {
    ObjectNode request = goldenRequest();
    ((ArrayNode) request.at("/prefetch/coverage/entry")).removeAll();

    assertOperationOutcome(post("/cds-services/order-sign-crd", request, clinicApi()), 400);
  }

  @Test
  void requestWithTwoCoveragesIs400() throws Exception {
    ObjectNode request = goldenRequest();
    ArrayNode entries = (ArrayNode) request.at("/prefetch/coverage/entry");
    entries.add(entries.get(0).deepCopy());

    assertOperationOutcome(post("/cds-services/order-sign-crd", request, clinicApi()), 400);
  }

  @Test
  void coverageWithAnotherPayerIs400() throws Exception {
    ObjectNode request = goldenRequest();
    ((ObjectNode) request.at("/prefetch/coverage/entry/0/resource/payor/0/identifier"))
        .put("value", "another-health-plan");

    assertOperationOutcome(post("/cds-services/order-sign-crd", request, clinicApi()), 400);
  }

  @Test
  void hookThatDoesNotMatchTheServiceIs400() throws Exception {
    ObjectNode request = goldenRequest();
    request.put("hook", "order-select");

    assertOperationOutcome(post("/cds-services/order-sign-crd", request, clinicApi()), 400);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "/hookInstance",
        "/context/patientId",
        "/context/draftOrders",
        "/context/draftOrders/entry/0/resource/id",
        "/prefetch/coverage/entry/0/resource/id"
      })
  void requestMissingARequiredFieldIs400(String pointer) throws Exception {
    ObjectNode request = goldenRequest();
    JsonNode parent = request.at(pointer.substring(0, pointer.lastIndexOf('/')));
    ((ObjectNode) parent).remove(pointer.substring(pointer.lastIndexOf('/') + 1));
    if (pointer.endsWith("resource/id")) {
      ((ObjectNode) request.at(pointer.substring(0, pointer.indexOf("/resource"))))
          .remove("fullUrl");
    }

    assertOperationOutcome(post("/cds-services/order-sign-crd", request, clinicApi()), 400);
  }

  @Test
  void orderSelectWithoutSelectionsIs400() throws Exception {
    ObjectNode request = goldenRequest();
    request.put("hook", "order-select");

    assertOperationOutcome(post("/cds-services/order-select-crd", request, clinicApi()), 400);
  }

  @Test
  void aSingleCoverageResourceIsAcceptedAsThePrefetch() throws Exception {
    givenPayerCoverage("PHP-10005", Coverage.CoverageStatus.ACTIVE, "2025-11-11", null);
    ObjectNode request = goldenRequest();
    ((ObjectNode) request.path("prefetch"))
        .set("coverage", request.at("/prefetch/coverage/entry/0/resource"));

    JsonNode response = okJson(post("/cds-services/order-sign-crd", request, clinicApi()));

    assertThat(
            subExtension(coverageInformation(response), "coverage")
                .at("/valueReference/reference")
                .asText())
        .isEqualTo("Coverage/coverage-sandra-okafor");
  }

  @Test
  void coveragePrefetchThatIsNotACoverageIs400() throws Exception {
    ObjectNode request = goldenRequest();
    ((ObjectNode) request.path("prefetch")).set("coverage", request.at("/prefetch/patient"));

    assertOperationOutcome(post("/cds-services/order-sign-crd", request, clinicApi()), 400);
  }

  @Test
  void invalidJsonIs400() throws Exception {
    assertOperationOutcome(post("/cds-services/order-sign-crd", "{not json", clinicApi()), 400);
  }

  @Test
  void unknownServiceIs404() throws Exception {
    assertOperationOutcome(post("/cds-services/nope", goldenRequest(), clinicApi()), 404);
  }

  @Test
  void hookCallWithoutATokenIs401() throws Exception {
    assertOperationOutcome(post("/cds-services/order-sign-crd", goldenRequest(), null), 401);
  }

  @Test
  void hookCallWithoutCoverageReadScopeIs403() throws Exception {
    String token =
        tokenService.issueSystemToken(TestAuth.CLIENT_ID, List.of("system/Claim.c")).accessToken();

    assertOperationOutcome(post("/cds-services/order-sign-crd", goldenRequest(), token), 403);
  }

  @Test
  void memberTokenCannotCallAHook() throws Exception {
    String token = tokenService.issueMemberToken(TestAuth.CLIENT_ID, "member-a").accessToken();

    assertOperationOutcome(post("/cds-services/order-sign-crd", goldenRequest(), token), 403);
  }

  @Test
  void hookCallRecordsLatencyAndWritesAnAuditEvent() throws Exception {
    givenPayerCoverage("PHP-10005", Coverage.CoverageStatus.ACTIVE, "2025-11-11", null);
    ObjectNode request = goldenRequest();
    request.put("hookInstance", "audit-check-instance");
    long before = latencyCount();

    okJson(post("/cds-services/order-sign-crd", request, clinicApi()));

    assertThat(latencyCount()).isEqualTo(before + 1);
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(PAYER_HAPI.created(AuditEvent.class))
                    .anySatisfy(
                        event -> {
                          assertThat(event.getEntityFirstRep().getName())
                              .isEqualTo("cds-services/order-sign-crd");
                          assertThat(event.getEntityFirstRep().getWhat().getIdentifier().getValue())
                              .isEqualTo("audit-check-instance");
                          assertThat(event.getAgentFirstRep().getWho().getIdentifier().getValue())
                              .isEqualTo(TestAuth.CLIENT_ID);
                          assertThat(event.getOutcome()).isEqualTo(AuditEvent.AuditEventOutcome._0);
                          assertThat(event.getEntity())
                              .extracting(entity -> entity.getWhat().getReference())
                              .contains("Coverage/payer-coverage");
                        }));
  }

  private void assertMatchesGolden(HttpResponse<String> response, String golden)
      throws IOException {
    JsonNode actual = okJson(response);
    Path written = Path.of("target", "cds-out", golden);
    Files.createDirectories(written.getParent());
    Files.writeString(
        written, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n");
    assertThat(actual)
        .as("hook response vs golden fixture crd/%s (actual in %s)", golden, written)
        .isEqualTo(objectMapper.readTree(fixture(golden)));
  }

  private long latencyCount() {
    var timer =
        meterRegistry
            .find(CrdHookService.LATENCY_METRIC)
            .tags("service", "order-sign-crd", "status", "200")
            .timer();
    return timer == null ? 0 : timer.count();
  }

  private static void givenPayerCoverage(
      String memberId, Coverage.CoverageStatus status, String start, String end) {
    Coverage coverage = MemberFixtures.coverage("payer-coverage", "member-1", status, start, end);
    coverage
        .addIdentifier()
        .setSystem("http://praxishealthplan.example/fhir/identifier/member-id")
        .setValue(memberId);
    PAYER_HAPI.givenSearchResult(Coverage.class, FhirClientStub.bundleOf(coverage));
  }

  private ObjectNode goldenRequest() throws IOException {
    return (ObjectNode) objectMapper.readTree(fixture("order-sign-request.json"));
  }

  private String clinicApi() {
    return tokenService.issueSystemToken(TestAuth.CLIENT_ID, TestAuth.CLIENT_SCOPES).accessToken();
  }

  private static JsonNode coverageInformation(JsonNode response) {
    for (JsonNode extension : response.at("/systemActions/0/resource/extension")) {
      if (CoverageDecisions.COVERAGE_INFORMATION.equals(extension.path("url").asText())) {
        return extension.path("extension");
      }
    }
    throw new AssertionError("No coverage-information extension in " + response);
  }

  private static JsonNode subExtension(JsonNode information, String url) {
    for (JsonNode extension : information) {
      if (url.equals(extension.path("url").asText())) {
        return extension;
      }
    }
    throw new AssertionError("No " + url + " in " + information);
  }

  private JsonNode okJson(HttpResponse<String> response) throws IOException {
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return objectMapper.readTree(response.body());
  }

  private void assertOperationOutcome(HttpResponse<String> response, int status)
      throws IOException {
    assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
    assertThat(objectMapper.readTree(response.body()).path("resourceType").asText())
        .isEqualTo("OperationOutcome");
  }

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String path, Object body, String token)
      throws IOException, InterruptedException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    body instanceof String text ? text : objectMapper.writeValueAsString(body)));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  static String fixture(String name) throws IOException {
    try (InputStream in = CdsHooksInteropTest.class.getResourceAsStream(FIXTURES + name)) {
      if (in == null) {
        throw new AssertionError("Missing golden fixture fixtures/davinci/crd/" + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String ehrFhirServerBase() {
    return "http://localhost:" + EHR_FHIR_SERVER.getAddress().getPort() + "/fhir";
  }

  private static HttpServer startEhrFhirServer() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext(
          "/fhir/Coverage",
          exchange -> {
            FALLBACK_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
            FALLBACK_QUERY.set(exchange.getRequestURI().getRawQuery());
            byte[] body = fetchedCoverageBundle().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/fhir+json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String fetchedCoverageBundle() {
    try {
      JsonNode request = new ObjectMapper().readTree(fixture("order-sign-request.json"));
      ObjectNode bundle = (ObjectNode) request.at("/prefetch/coverage").deepCopy();
      ((ObjectNode) bundle.at("/entry/0/resource")).put("id", "fetched-coverage");
      ((ObjectNode) bundle.at("/entry/0")).remove("fullUrl");
      return bundle.toString();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
