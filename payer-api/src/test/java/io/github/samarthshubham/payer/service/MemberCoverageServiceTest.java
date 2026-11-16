package io.github.samarthshubham.payer.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.samarthshubham.payer.mapper.MemberMapper;
import io.github.samarthshubham.payer.support.FhirClientStub;
import io.github.samarthshubham.payer.support.MemberFixtures;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Coverage;
import org.junit.jupiter.api.Test;

class MemberCoverageServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

  private final FhirClientStub payerHapi = new FhirClientStub();
  private final MemberCoverageService service = new MemberCoverageService(payerHapi.client());

  @Test
  void activeCoverageOnTheDateIsVerified() {
    givenCoverages(coverage("c1", Coverage.CoverageStatus.ACTIVE, "2026-01-01", "2026-12-31"));

    assertThat(service.check(" PHP-10001 ", TODAY))
        .isEqualTo(new MemberCoverageCheck(MemberCoverageCheck.Status.ACTIVE, "c1"));
    assertThat(payerHapi.lastSearchCriteria(Coverage.class))
        .contains("identifier=" + MemberMapper.MEMBER_ID_SYSTEM + "|PHP-10001");
  }

  @Test
  void theActiveCoverageWinsOverAnEndedOne() {
    givenCoverages(
        coverage("old", Coverage.CoverageStatus.CANCELLED, "2024-01-01", "2024-12-31"),
        coverage("new", Coverage.CoverageStatus.ACTIVE, "2026-01-01", null));

    assertThat(service.check("PHP-10001", TODAY).payerCoverageId()).isEqualTo("new");
  }

  @Test
  void cancelledOrOutOfPeriodCoverageIsNotActive() {
    givenCoverages(
        coverage("cancelled", Coverage.CoverageStatus.CANCELLED, "2025-01-01", null),
        coverage("future", Coverage.CoverageStatus.ACTIVE, "2027-01-01", null),
        coverage("ended", Coverage.CoverageStatus.ACTIVE, "2025-01-01", "2026-11-10"));

    assertThat(service.check("PHP-10001", TODAY))
        .isEqualTo(
            new MemberCoverageCheck(MemberCoverageCheck.Status.NO_ACTIVE_COVERAGE, "cancelled"));
  }

  @Test
  void coverageOnItsFirstAndLastDayIsActive() {
    givenCoverages(coverage("c1", Coverage.CoverageStatus.ACTIVE, "2026-11-11", "2026-11-11"));

    assertThat(service.check("PHP-10001", TODAY).verified()).isTrue();
  }

  @Test
  void noCoverageOrNoMemberIdMeansNoMemberFound() {
    givenCoverages();

    assertThat(service.check("PHP-99999", TODAY).status())
        .isEqualTo(MemberCoverageCheck.Status.NO_MEMBER_FOUND);
    assertThat(service.check(null, TODAY).status())
        .isEqualTo(MemberCoverageCheck.Status.NO_MEMBER_FOUND);
    assertThat(service.check(" ", TODAY).status())
        .isEqualTo(MemberCoverageCheck.Status.NO_MEMBER_FOUND);
    assertThat(payerHapi.searchCount(Coverage.class)).isEqualTo(1);
  }

  private void givenCoverages(Coverage... coverages) {
    payerHapi.givenSearchResult(Coverage.class, FhirClientStub.bundleOf(coverages));
  }

  private static Coverage coverage(
      String id, Coverage.CoverageStatus status, String start, String end) {
    return MemberFixtures.coverage(id, "member-1", status, start, end);
  }
}
