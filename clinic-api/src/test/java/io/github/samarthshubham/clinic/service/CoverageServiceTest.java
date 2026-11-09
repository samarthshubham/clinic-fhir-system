package io.github.samarthshubham.clinic.service;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.github.samarthshubham.clinic.dto.CoverageRequest;
import io.github.samarthshubham.clinic.dto.CoverageResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.CoverageMapper;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoverageServiceTest {

  private static final CoverageRequest REQUEST =
      new CoverageRequest(
          "p1",
          "praxis-health-plan",
          "Praxis Health Plan",
          "PHP-10001",
          "Praxis Gold PPO",
          "active",
          LocalDate.of(2026, 1, 1),
          null,
          null);

  private FhirClientStub fhir;
  private CoverageService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service = new CoverageService(fhir.client(), new CoverageMapper());
  }

  @Test
  void createRegistersTheMissingPayerThenTheCoverage() {
    CoverageResponse coverage = service.createCoverage(REQUEST);

    assertThat(fhir.created())
        .extracting(resource -> resource.getClass().getSimpleName())
        .containsExactly("Organization", "Coverage");
    assertThat(fhir.lastSearchCriteria(Organization.class))
        .containsExactly("identifier=" + CoverageMapper.PAYER_SYSTEM + "|praxis-health-plan");
    assertThat(coverage.id()).isEqualTo("2");
    assertThat(coverage.payerOrganizationId()).isEqualTo("1");
    assertThat(coverage.memberId()).isEqualTo("PHP-10001");
  }

  @Test
  void createReusesAnExistingPayer() {
    Organization payer = new Organization();
    payer.setId("seed-praxis-health-plan");
    fhir.givenSearchResult(Organization.class, bundleOf(payer));

    CoverageResponse coverage = service.createCoverage(REQUEST);

    assertThat(fhir.created()).singleElement().isInstanceOf(Coverage.class);
    assertThat(coverage.payerOrganizationId()).isEqualTo("seed-praxis-health-plan");
  }

  @Test
  void getReturnsStoredCoverage() {
    fhir.givenStored(new Coverage().setStatus(Coverage.CoverageStatus.ACTIVE), "cov1");

    assertThat(service.getCoverage("cov1").status()).isEqualTo("active");
  }

  @Test
  void getUnknownCoverageThrowsNotFound() {
    assertThatThrownBy(() -> service.getCoverage("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Coverage with id 'missing' not found");
  }

  @Test
  void getPropagatesOtherServerErrors() {
    fhir.givenReadFails(Coverage.class, "x", new InternalErrorException("FHIR server error"));

    assertThatThrownBy(() -> service.getCoverage("x")).isInstanceOf(InternalErrorException.class);
  }

  @Test
  void listSearchesByBeneficiary() {
    Coverage stored = new Coverage().setBeneficiary(new Reference("Patient/p1"));
    stored.setId("cov1");
    fhir.givenSearchResult(Coverage.class, bundleOf(stored));

    assertThat(service.getCoveragesForPatient("p1").items())
        .extracting(CoverageResponse::id)
        .containsExactly("cov1");
    assertThat(fhir.lastSearchCriteria(Coverage.class)).containsExactly("beneficiary=Patient/p1");
  }

  @Test
  void activeCoverageIsTheLatestActiveOne() {
    Coverage stored = new Coverage().setStatus(Coverage.CoverageStatus.ACTIVE);
    stored.setId("cov1");
    fhir.givenSearchResult(Coverage.class, bundleOf(stored));

    assertThat(service.findActiveCoverage("p1").id()).isEqualTo("cov1");
    assertThat(fhir.lastSearchCriteria(Coverage.class))
        .containsExactlyInAnyOrder(
            "beneficiary=Patient/p1", "status=active", "_sort=-_lastUpdated", "_count=1");
  }

  @Test
  void activeCoverageIsNullWhenThereIsNone() {
    assertThat(service.findActiveCoverage("p1")).isNull();
  }
}
