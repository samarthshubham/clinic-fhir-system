package io.github.samarthshubham.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ca.uhn.fhir.context.FhirContext;
import io.github.samarthshubham.clinic.client.PayerApiException;
import io.github.samarthshubham.clinic.config.PayerApiProperties;
import io.github.samarthshubham.clinic.dto.CoverageResponse;
import java.time.Duration;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.InsurancePlan;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DirectoryServiceTest {
  private static final FhirContext FHIR = FhirContext.forR4Cached();

  private static final String PAYER = "http://payer.test";
  private final CoverageService coverageService = mock(CoverageService.class);
  private MockRestServiceServer http;
  private DirectoryService directory;

  @BeforeEach
  void setUp() {
    var builder = RestClient.builder().baseUrl(PAYER);
    http = MockRestServiceServer.bindTo(builder).build();
    directory =
        new DirectoryService(
            coverageService,
            new PayerApiProperties(PAYER, "clinic", "test", "", Duration.ofSeconds(2), "praxis"),
            builder.build(),
            FHIR);
  }

  @Test
  void usesOnlyPublicPlanAndNetworkSearchesForAPatientWithAConfiguredPayer() {
    cover("praxis", LocalDate.now().minusDays(1), null);
    var plan = resource("insurance-plan", InsurancePlan.class);
    var plans = bundle(plan);
    http.expect(requestTo(startsWith(PAYER + "/fhir/InsurancePlan?name:exact=")))
        .andExpect(queryParam("name:exact", "Praxis%20Gold%20PPO"))
        .andExpect(headerDoesNotExist("Authorization"))
        .andRespond(
            withSuccess(
                FHIR.newJsonParser().encodeResourceToString(plans), MediaType.APPLICATION_JSON));
    http.expect(requestTo(startsWith(PAYER + "/fhir/PractitionerRole?")))
        .andExpect(queryParam("network", "Organization%2Fpraxis-standard-network"))
        .andExpect(headerDoesNotExist("Authorization"))
        .andRespond(
            withSuccess(
                FHIR.newJsonParser().encodeResourceToString(directoryPage()),
                MediaType.APPLICATION_JSON));

    var result = directory.search("private-patient", "internal medicine", "Springfield");
    assertThat(result.planName()).isEqualTo("Praxis Gold PPO");
    assertThat(result.providers())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.name()).isEqualTo("Aisha Patel, MD");
              assertThat(row.address()).contains("Springfield");
              assertThat(row.specialties()).containsExactly("Internal Medicine");
            });
    http.verify();
  }

  @Test
  void doesNotQueryThePayerForMissingOtherPayerFutureOrExpiredCoverage() {
    assertThat(directory.search("private-patient", "", "").planName()).isNull();
    cover("another-payer", null, null);
    assertThat(directory.search("private-patient", "", "").providers()).isEmpty();
    cover("praxis", LocalDate.now().plusDays(3), null);
    assertThat(directory.search("private-patient", "", "").providers()).isEmpty();
    cover("praxis", null, LocalDate.now().minusDays(3));
    assertThat(directory.search("private-patient", "", "").providers()).isEmpty();
    http.verify();
  }

  @Test
  void reportsPayerFailure() {
    cover("praxis", null, null);
    http.expect(requestTo(startsWith(PAYER + "/fhir/InsurancePlan?")))
        .andRespond(withServiceUnavailable());
    assertThatThrownBy(() -> directory.search("private-patient", "", ""))
        .isInstanceOf(PayerApiException.class);
    http.verify();
  }

  @Test
  void aPlanWithoutNetworksDoesNotOfferInNetworkResults() {
    cover("praxis", null, null);
    var plan = resource("insurance-plan", InsurancePlan.class);
    plan.getNetwork().clear();
    http.expect(requestTo(startsWith(PAYER + "/fhir/InsurancePlan?")))
        .andRespond(
            withSuccess(
                FHIR.newJsonParser().encodeResourceToString(bundle(plan)),
                MediaType.APPLICATION_JSON));
    assertThat(directory.search("private-patient", "", "").planName()).isNull();
    http.verify();
  }

  private void cover(String payer, LocalDate start, LocalDate end) {
    when(coverageService.findActiveCoverage("private-patient"))
        .thenReturn(
            new CoverageResponse(
                "coverage",
                "private-patient",
                "payer",
                payer,
                "Payer",
                "private-member",
                "Praxis Gold PPO",
                "active",
                start,
                end,
                "self"));
  }

  private static Bundle directoryPage() {
    return bundle(
        resource("practitioner-role-aisha", PractitionerRole.class),
        resource("practitioner", Practitioner.class),
        resource("location-clinic", Location.class),
        resource("organization-clinic", Organization.class));
  }

  private static Bundle bundle(Resource... resources) {
    var bundle = new Bundle().setType(Bundle.BundleType.SEARCHSET);
    for (Resource resource : resources) {
      var entry = bundle.addEntry().setResource(resource);
      entry
          .getSearch()
          .setMode(
              resource instanceof PractitionerRole || resources.length == 1
                  ? Bundle.SearchEntryMode.MATCH
                  : Bundle.SearchEntryMode.INCLUDE);
    }
    return bundle;
  }

  private static <T extends Resource> T resource(String name, Class<T> type) {
    try (var fixture =
        new ClassPathResource("fixtures/davinci/plan-net/" + name + ".json").getInputStream()) {
      return FHIR.newJsonParser().parseResource(type, fixture);
    } catch (java.io.IOException exception) {
      throw new java.io.UncheckedIOException(exception);
    }
  }
}
