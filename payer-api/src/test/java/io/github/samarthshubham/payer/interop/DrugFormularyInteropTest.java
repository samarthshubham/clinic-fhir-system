package io.github.samarthshubham.payer.interop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Basic;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.MedicationKnowledge;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DrugFormularyInteropTest {

  private static final FhirContext FHIR = FhirContext.forR4Cached();
  private static final String FORMULARY_PROFILE =
      "http://hl7.org/fhir/us/davinci-drug-formulary/StructureDefinition/usdf-";

  @LocalServerPort private int port;

  @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
  private IGenericClient payerHapi;

  static Stream<Arguments> resourceQueries() {
    return Stream.of(
        Arguments.of(
            "InsurancePlan",
            "formulary",
            "Formulary",
            "type=DRUGPOL&identifier=PHP-F-2026&status=active&period=ge2026-01-01&name=Praxis"),
        Arguments.of(
            "Basic",
            "formulary-item",
            "FormularyItem",
            "formulary=InsurancePlan/praxis-formulary-2026&drug-tier=generic&pharmacy-benefit-type=1-month-in-retail&subject:MedicationKnowledge.drug-name=metformin&status=active&period=ge2026-01-01&_include=Basic:subject"),
        Arguments.of(
            "MedicationKnowledge",
            "formulary-drug",
            "FormularyDrug",
            "code=861007&drug-name=metformin&doseform=317541&status=active"));
  }

  @ParameterizedTest
  @MethodSource("resourceQueries")
  @SuppressWarnings("unchecked")
  void anonymousSearchPreservesIgFilters(
      String type, String filename, String profile, String filters) throws Exception {
    Resource resource = fixture(filename);
    Bundle stored = searchResult();
    stored.addEntry().setResource(resource).getSearch().setMode(Bundle.SearchEntryMode.MATCH);
    IQuery<Bundle> search = mock(IQuery.class);
    when(search.execute()).thenReturn(stored);
    when(payerHapi.search().byUrl(anyString()).returnBundle(Bundle.class)).thenReturn(search);

    var response = get("/" + type + "?" + filters);

    assertThat(response.statusCode()).isEqualTo(200);
    Bundle publicBundle = FHIR.newJsonParser().parseResource(Bundle.class, response.body());
    assertThat(publicBundle.getEntry())
        .singleElement()
        .satisfies(
            entry ->
                assertThat(entry.getFullUrl())
                    .endsWith("/" + type + "/" + resource.getIdElement().getIdPart()));
    verify(payerHapi.search())
        .byUrl(
            org.mockito.ArgumentMatchers.argThat(
                url -> {
                  String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
                  return decoded.startsWith(type + "?")
                      && Stream.of(filters.split("&")).allMatch(decoded::contains)
                      && decoded.contains(FORMULARY_PROFILE + profile);
                }));
  }

  @Test
  void basicIncludesReturnOnlyPublishedResources() throws Exception {
    Bundle stored = searchResult();
    stored
        .addEntry()
        .setResource(fixture("formulary-item"))
        .getSearch()
        .setMode(Bundle.SearchEntryMode.MATCH);
    stored
        .addEntry()
        .setResource(fixture("formulary-drug"))
        .getSearch()
        .setMode(Bundle.SearchEntryMode.INCLUDE);
    stored
        .addEntry()
        .setResource(new MedicationKnowledge().setId("unpublished"))
        .getSearch()
        .setMode(Bundle.SearchEntryMode.INCLUDE);
    stored
        .addEntry()
        .setResource(new Patient().setId("private"))
        .getSearch()
        .setMode(Bundle.SearchEntryMode.INCLUDE);
    when(payerHapi.search().byUrl(anyString()).returnBundle(Bundle.class).execute())
        .thenReturn(stored);

    var response = get("/Basic?_include=Basic:subject");

    assertThat(response.statusCode()).isEqualTo(200);
    Bundle result = FHIR.newJsonParser().parseResource(Bundle.class, response.body());
    assertThat(result.getEntry())
        .extracting(entry -> entry.getResource().getIdElement().getIdPart())
        .containsExactly(
            "praxis-formulary-2026-metformin-500-mg-tablet", "metformin-500-mg-tablet");
  }

  @Test
  void anonymousReadRequiresTheFormularyItemProfile() throws Exception {
    doReturn(new Basic()).when(payerHapi).read(Basic.class, "unpublished");
    assertThat(get("/Basic/unpublished").statusCode()).isEqualTo(404);
  }

  @Test
  void addingFormularyTypesDoesNotPublishContainedRecords() throws Exception {
    var item = (Basic) fixture("formulary-item");
    item.addContained(new Basic().setId("internal"));
    doReturn(item).when(payerHapi).read(Basic.class, item.getIdElement().getIdPart());

    assertThat(get("/Basic/" + item.getIdElement().getIdPart()).statusCode()).isEqualTo(404);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/Basic?subject:Patient.name=example",
        "/Basic?_include=Basic:author",
        "/MedicationKnowledge?_has:Basic:subject:status=active"
      })
  void unsupportedPublicSearchesAreRejectedBeforeTheStore(String path) throws Exception {
    assertThat(get(path).statusCode()).isEqualTo(400);
    verifyNoInteractions(payerHapi);
  }

  @Test
  void metadataAdvertisesBothCatalogsOnInsurancePlan() throws Exception {
    var statement =
        FHIR.newJsonParser().parseResource(CapabilityStatement.class, get("/metadata").body());
    var plan =
        statement.getRestFirstRep().getResource().stream()
            .filter(resource -> resource.getType().equals("InsurancePlan"))
            .findFirst()
            .orElseThrow();
    assertThat(plan.getSupportedProfile())
        .extracting(profile -> profile.getValue())
        .contains(
            FORMULARY_PROFILE + "PayerInsurancePlan",
            FORMULARY_PROFILE + "Formulary",
            "http://hl7.org/fhir/us/davinci-pdex-plan-net/StructureDefinition/plannet-InsurancePlan");
    assertThat(plan.getSearchParam())
        .extracting(parameter -> parameter.getName())
        .contains("formulary-coverage", "coverage-type", "coverage-area", "owned-by")
        .doesNotHaveDuplicates();
    assertThat(plan.getSearchInclude())
        .extracting(include -> include.getValue())
        .contains("InsurancePlan:formulary-coverage", "InsurancePlan:coverage-area");
  }

  private static Bundle searchResult() {
    return new Bundle().setType(Bundle.BundleType.SEARCHSET).setTotal(1);
  }

  private HttpResponse<String> get(String path) throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/fhir" + path))
              .header("Accept", "application/fhir+json")
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }

  private static Resource fixture(String name) throws Exception {
    try (InputStream stream =
        DrugFormularyInteropTest.class.getResourceAsStream(
            "/fixtures/davinci/formulary/" + name + ".json")) {
      return (Resource) FHIR.newJsonParser().parseResource(stream);
    }
  }
}
