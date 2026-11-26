package io.github.samarthshubham.payer.interop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceGoneException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnclassifiedServerFailureException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.Enumerations.SearchParamType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlanNetDirectoryInteropTest {

  private static final FhirContext FHIR = FhirContext.forR4Cached();

  @LocalServerPort private int port;

  @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
  private IGenericClient payerHapi;

  static Stream<Arguments> directoryResources() {
    return Stream.of(
            "practitioner",
            "practitioner-role-aisha",
            "organization-clinic",
            "location-clinic",
            "healthcare-service",
            "insurance-plan",
            "endpoint",
            "organization-affiliation")
        .map(filename -> Arguments.of(fixture(filename)));
  }

  @ParameterizedTest
  @MethodSource("directoryResources")
  void anonymousClientReadsPublishedResource(Resource resource) throws Exception {
    String id = resource.getIdElement().getIdPart();
    doAnswer(ignored -> resource.copy()).when(payerHapi).read(resource.getClass(), id);

    var response = get("/" + resource.fhirType() + "/" + id);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(FHIR.newJsonParser().parseResource(response.body()).fhirType())
        .isEqualTo(resource.fhirType());
  }

  @Test
  void anonymousClientSearchesAndFollowsDirectoryPagination() throws Exception {
    var practitioner = fixture("practitioner");
    var role = fixture("practitioner-role-aisha");
    Practitioner unpublished = new Practitioner();
    unpublished.setId("private-practitioner");
    Patient nonDirectoryResource = new Patient();
    nonDirectoryResource.setId("private-patient");
    Bundle upstream = new Bundle().setType(Bundle.BundleType.SEARCHSET).setTotal(2);
    upstream.addEntry().setResource(role).getSearch().setMode(Bundle.SearchEntryMode.MATCH);
    upstream
        .addEntry()
        .setResource(practitioner)
        .getSearch()
        .setMode(Bundle.SearchEntryMode.INCLUDE);
    upstream
        .addEntry()
        .setResource(unpublished)
        .getSearch()
        .setMode(Bundle.SearchEntryMode.INCLUDE);
    upstream
        .addEntry()
        .setResource(nonDirectoryResource)
        .getSearch()
        .setMode(Bundle.SearchEntryMode.INCLUDE);
    upstream.addLink().setRelation("next").setUrl("http://payer-hapi:8080/fhir?_getpages=internal");
    when(payerHapi.search().byUrl(anyString()).returnBundle(Bundle.class).execute())
        .thenReturn(upstream, new Bundle().setType(Bundle.BundleType.SEARCHSET));

    var response =
        get(
            "/PractitionerRole?specialty=207R00000X"
                + "&location:Location.address-city=Springfield&_include=PractitionerRole:practitioner&_count=1");
    assertThat(response.statusCode()).isEqualTo(200);
    Bundle result = FHIR.newJsonParser().parseResource(Bundle.class, response.body());
    assertThat(result.getTotal()).isEqualTo(2);
    assertThat(result.getEntry())
        .extracting(entry -> entry.getResource().fhirType())
        .containsExactly("PractitionerRole", "Practitioner");
    assertThat(result.getEntry())
        .allSatisfy(entry -> assertThat(entry.getFullUrl()).startsWith(baseUrl() + "/"));
    String next = result.getLink("next").getUrl();
    assertThat(next).startsWith(baseUrl() + "/PractitionerRole?").endsWith("_offset=1");
    var page = get(next.substring(baseUrl().length()));
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(FHIR.newJsonParser().parseResource(Bundle.class, page.body()).getLink("previous"))
        .isNotNull();

    var urls = ArgumentCaptor.forClass(String.class);
    verify(payerHapi.search(), org.mockito.Mockito.times(3)).byUrl(urls.capture());
    assertThat(urls.getAllValues().subList(1, 3))
        .allSatisfy(
            url ->
                assertThat(URLDecoder.decode(url, StandardCharsets.UTF_8))
                    .contains(
                        "PractitionerRole?",
                        "_profile=http://hl7.org/fhir/us/davinci-pdex-plan-net/StructureDefinition/plannet-PractitionerRole",
                        "location:Location.address-city=Springfield",
                        "_include=PractitionerRole:practitioner"));
  }

  @Test
  void vreadChecksCurrentAndHistoricPublication() throws Exception {
    Practitioner published = (Practitioner) fixture("practitioner");
    Practitioner beforePublication = new Practitioner();
    beforePublication.setId("aisha-patel");
    doReturn(published).when(payerHapi).read(Practitioner.class, "aisha-patel");
    doReturn(beforePublication).when(payerHapi).vread(Practitioner.class, "aisha-patel", "1");
    doReturn(published).when(payerHapi).vread(Practitioner.class, "aisha-patel", "2");

    assertThat(get("/Practitioner/aisha-patel/_history/1").statusCode()).isEqualTo(404);
    assertThat(get("/Practitioner/aisha-patel/_history/2").statusCode()).isEqualTo(200);
  }

  @Test
  void directoryEndpointDoesNotExposeConnectionHeaders() throws Exception {
    Endpoint endpoint = (Endpoint) fixture("endpoint");
    endpoint.addHeader("Authorization: example-private-credential");
    doReturn(endpoint).when(payerHapi).read(Endpoint.class, endpoint.getIdElement().getIdPart());

    var response = get("/Endpoint/" + endpoint.getIdElement().getIdPart());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(FHIR.newJsonParser().parseResource(Endpoint.class, response.body()).hasHeader())
        .isFalse();
  }

  @Test
  void nonDirectoryPractitionerIsNotPublic() throws Exception {
    doReturn(new Practitioner()).when(payerHapi).read(Practitioner.class, "private");

    assertThat(get("/Practitioner/private").statusCode()).isEqualTo(404);
  }

  @Test
  void directoryEntryCannotPublishAContainedMember() throws Exception {
    Practitioner practitioner = (Practitioner) fixture("practitioner");
    practitioner.addContained(new Patient().setId("private-member"));
    doReturn(practitioner).when(payerHapi).read(Practitioner.class, "aisha-patel");

    assertThat(get("/Practitioner/aisha-patel").statusCode()).isEqualTo(404);
  }

  @Test
  void allowsReverseChainingAcrossDirectoryTypes() throws Exception {
    when(payerHapi.search().byUrl(anyString()).returnBundle(Bundle.class).execute())
        .thenReturn(new Bundle().setType(Bundle.BundleType.SEARCHSET));

    assertThat(
            get("/Organization?_has:Location:organization:address-city=Springfield").statusCode())
        .isEqualTo(200);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "_include=*",
        "_revinclude=Patient:general-practitioner",
        "_profile=private",
        "practitioner:Patient.name=Example",
        "practitioner.birthdate=2020",
        "_has:Patient:general-practitioner:name=Example",
        "_count=1&_count=2",
        "_offset=-1"
      })
  void unsupportedFiltersDoNotReachStore(String query) throws Exception {
    var response = get("/PractitionerRole?" + query.replace("*", "%2A"));
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(FHIR.newJsonParser().parseResource(response.body()))
        .isInstanceOf(OperationOutcome.class);
    verifyNoInteractions(payerHapi);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/Patient", "/Practitioner/_history", "/Practitioner/$everything"})
  void otherAnonymousInteractionsAreDenied(String path) throws Exception {
    assertThat(get(path).statusCode()).isBetween(400, 499);
    verifyNoInteractions(payerHapi);
  }

  @Test
  void metadataAdvertisesBothOrganizationProfilesAndPlanNetSearch() throws Exception {
    var statement =
        FHIR.newJsonParser().parseResource(CapabilityStatement.class, get("/metadata").body());
    var resources = statement.getRestFirstRep().getResource();
    var organization =
        resources.stream()
            .filter(resource -> resource.getType().equals("Organization"))
            .findFirst()
            .orElseThrow();
    assertThat(organization.getSupportedProfile()).hasSize(2);
    var role =
        resources.stream()
            .filter(resource -> resource.getType().equals("PractitionerRole"))
            .findFirst()
            .orElseThrow();
    assertThat(role.getSearchInclude())
        .anyMatch(include -> include.getValue().equals("PractitionerRole:network"));
    assertThat(role.getSearchParam())
        .anyMatch(
            parameter ->
                parameter.getName().equals("network")
                    && parameter.getType() == SearchParamType.REFERENCE);
  }

  @Test
  void storeFailuresReturnFhirErrorsWithoutUpstreamDiagnostics() throws Exception {
    Map<String, RuntimeException> errors =
        Map.of(
            "missing", new ResourceNotFoundException("internal store"),
            "gone", new ResourceGoneException("internal store"),
            "invalid", new InvalidRequestException("internal store"),
            "offline", new FhirClientConnectionException("internal store"),
            "failure", new UnclassifiedServerFailureException(500, "internal store"));
    Map<String, Integer> statuses =
        Map.of("missing", 404, "gone", 404, "invalid", 400, "offline", 503, "failure", 502);
    for (var entry : errors.entrySet()) {
      doThrow(entry.getValue()).when(payerHapi).read(Practitioner.class, entry.getKey());
      var response = get("/Practitioner/" + entry.getKey());
      assertThat(response.statusCode()).isEqualTo(statuses.get(entry.getKey()));
      assertThat(response.body()).doesNotContain("internal store");
      assertThat(FHIR.newJsonParser().parseResource(response.body()))
          .isInstanceOf(OperationOutcome.class);
    }
  }

  private HttpResponse<String> get(String path) throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(URI.create(baseUrl() + path))
              .header("Accept", "application/fhir+json")
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }

  private String baseUrl() {
    return "http://localhost:" + port + "/fhir";
  }

  private static Resource fixture(String filename) {
    try (InputStream in =
        PlanNetDirectoryInteropTest.class.getResourceAsStream(
            "/fixtures/davinci/plan-net/" + filename + ".json")) {
      return (Resource) FHIR.newJsonParser().parseResource(in);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to read Plan-Net fixture " + filename, e);
    }
  }
}
