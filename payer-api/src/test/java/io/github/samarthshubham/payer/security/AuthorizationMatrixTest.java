package io.github.samarthshubham.payer.security;

import static io.github.samarthshubham.payer.security.AuthorizationMatrixTest.Caller.ANONYMOUS;
import static io.github.samarthshubham.payer.security.AuthorizationMatrixTest.Caller.CLINIC_API;
import static io.github.samarthshubham.payer.security.AuthorizationMatrixTest.Caller.MEMBER_A;
import static io.github.samarthshubham.payer.security.AuthorizationMatrixTest.Caller.MEMBER_B;
import static io.github.samarthshubham.payer.security.AuthorizationMatrixTest.Caller.SYSTEM_READER;
import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import io.github.samarthshubham.payer.service.AuthService;
import io.github.samarthshubham.payer.service.ClientCredentialsService;
import io.github.samarthshubham.payer.service.TokenService;
import io.github.samarthshubham.payer.support.TestAuth;
import io.github.samarthshubham.payer.support.TestInteropProviders;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Every JSON endpoint and every interop operation × every kind of caller. Interop rows use the
 * test-only providers in {@link TestInteropProviders} until real providers exist; later stories add
 * their own rows here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestInteropProviders.class)
class AuthorizationMatrixTest {

  enum Caller {
    ANONYMOUS,
    UM_REVIEWER,
    PAYER_ADMIN,
    CLINIC_API,
    SYSTEM_READER,
    MEMBER_A,
    MEMBER_B
  }

  private static final Set<Caller> EVERYONE = EnumSet.allOf(Caller.class);
  private static final String FORM = "application/x-www-form-urlencoded";
  private static final String JSON = "application/json";
  private static final String FHIR_JSON = "application/fhir+json";
  private static final String EMPTY_PARAMETERS = "{\"resourceType\":\"Parameters\"}";

  private static final List<Endpoint> ENDPOINTS =
      List.of(
          json(
              "POST", "/api/auth/login", JSON, "{\"username\":\"u\",\"password\":\"p\"}", EVERYONE),
          json("POST", "/api/auth/refresh", JSON, "{\"refreshToken\":\"r\"}", EVERYONE),
          json("POST", "/oauth/token", FORM, "grant_type=client_credentials", EVERYONE),
          json("GET", "/actuator/health", null, null, EVERYONE),
          fhir("GET", "/fhir/metadata", null, EVERYONE),
          fhir("GET", "/fhir/Patient/member-a", null, EnumSet.of(SYSTEM_READER, MEMBER_A)),
          fhir("GET", "/fhir/Patient/member-b", null, EnumSet.of(SYSTEM_READER, MEMBER_B)),
          fhir("GET", "/fhir/Patient", null, EnumSet.of(SYSTEM_READER)),
          fhir("GET", "/fhir/Claim/claim-a", null, EnumSet.of(SYSTEM_READER, MEMBER_A)),
          fhir(
              "POST",
              "/fhir/Claim",
              "{\"resourceType\":\"Claim\",\"patient\":{\"reference\":\"Patient/member-a\"}}",
              EnumSet.of(CLINIC_API)),
          fhir("POST", "/fhir/Claim/$test-submit", EMPTY_PARAMETERS, EnumSet.of(CLINIC_API)),
          fhir(
              "POST", "/fhir/Claim/$test-unmapped", EMPTY_PARAMETERS, EnumSet.noneOf(Caller.class)),
          fhir("GET", "/fhir/Questionnaire/q1", null, EnumSet.of(CLINIC_API, SYSTEM_READER)));

  private final HttpClient http = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Autowired private TokenService tokenService;

  @MockitoBean private IGenericClient payerHapiClient;
  @MockitoBean private AuthService authService;
  @MockitoBean private ClientCredentialsService clientCredentialsService;

  static Stream<Arguments> endpointsByCaller() {
    List<Arguments> cases = new ArrayList<>();
    for (Endpoint endpoint : ENDPOINTS) {
      for (Caller caller : Caller.values()) {
        cases.add(Arguments.of(endpoint, caller));
      }
    }
    return cases.stream();
  }

  @ParameterizedTest(name = "{1} {0}")
  @MethodSource("endpointsByCaller")
  void eachCallerGetsExactlyItsAllowedEndpoints(Endpoint endpoint, Caller caller)
      throws IOException, InterruptedException {
    HttpResponse<String> response = send(endpoint, caller);

    if (endpoint.allowed().contains(caller)) {
      assertThat(response.statusCode()).isBetween(200, 299);
      return;
    }
    assertThat(response.statusCode()).isEqualTo(caller == ANONYMOUS ? 401 : 403);
    if (endpoint.fhir()) {
      assertThat(response.body()).contains("OperationOutcome");
    } else {
      assertThat(response.body()).contains("\"status\":" + response.statusCode());
    }
  }

  private HttpResponse<String> send(Endpoint endpoint, Caller caller)
      throws IOException, InterruptedException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + endpoint.path()))
            .method(
                endpoint.method(),
                endpoint.body() == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(endpoint.body()));
    if (endpoint.contentType() != null) {
      request.header("Content-Type", endpoint.contentType());
    }
    if (caller != ANONYMOUS) {
      request.header("Authorization", "Bearer " + token(caller));
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String token(Caller caller) {
    return switch (caller) {
      case ANONYMOUS -> throw new IllegalArgumentException("anonymous has no token");
      case UM_REVIEWER ->
          tokenService.issueStaffTokens(TestAuth.user(Role.UM_REVIEWER)).accessToken();
      case PAYER_ADMIN ->
          tokenService.issueStaffTokens(TestAuth.user(Role.PAYER_ADMIN)).accessToken();
      case CLINIC_API ->
          tokenService.issueSystemToken(TestAuth.CLIENT_ID, TestAuth.CLIENT_SCOPES).accessToken();
      case SYSTEM_READER ->
          tokenService.issueSystemToken("reader", List.of("system/*.rs")).accessToken();
      case MEMBER_A ->
          tokenService
              .issueMemberToken(TestAuth.CLIENT_ID, TestInteropProviders.MEMBER_A)
              .accessToken();
      case MEMBER_B ->
          tokenService
              .issueMemberToken(TestAuth.CLIENT_ID, TestInteropProviders.MEMBER_B)
              .accessToken();
    };
  }

  private static Endpoint json(
      String method, String path, String contentType, String body, Set<Caller> allowed) {
    return new Endpoint(method, path, contentType, body, allowed, false);
  }

  private static Endpoint fhir(String method, String path, String body, Set<Caller> allowed) {
    return new Endpoint(method, path, body == null ? null : FHIR_JSON, body, allowed, true);
  }

  record Endpoint(
      String method,
      String path,
      String contentType,
      String body,
      Set<Caller> allowed,
      boolean fhir) {
    @Override
    public String toString() {
      return method + " " + path;
    }
  }
}
