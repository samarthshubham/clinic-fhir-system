package io.github.samarthshubham.payer.service;

import static io.github.samarthshubham.payer.support.FhirClientStub.bundleOf;
import static io.github.samarthshubham.payer.support.MemberFixtures.GOLD_PLAN_ID;
import static io.github.samarthshubham.payer.support.MemberFixtures.GOLD_PLAN_NAME;
import static io.github.samarthshubham.payer.support.MemberFixtures.coverage;
import static io.github.samarthshubham.payer.support.MemberFixtures.decision;
import static io.github.samarthshubham.payer.support.MemberFixtures.goldPlan;
import static io.github.samarthshubham.payer.support.MemberFixtures.member;
import static io.github.samarthshubham.payer.support.MemberFixtures.preAuth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.samarthshubham.payer.dto.MemberResponse;
import io.github.samarthshubham.payer.dto.MemberSummary;
import io.github.samarthshubham.payer.dto.PageResponse;
import io.github.samarthshubham.payer.dto.PlanSummary;
import io.github.samarthshubham.payer.dto.PriorAuthSummary;
import io.github.samarthshubham.payer.exception.ResourceNotFoundException;
import io.github.samarthshubham.payer.mapper.MemberMapper;
import io.github.samarthshubham.payer.support.FhirClientStub;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.InsurancePlan;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MemberServiceTest {

  private static final String MEMBER_IDS = "identifier=" + MemberMapper.MEMBER_ID_SYSTEM + "|";

  private FhirClientStub fhir;
  private MemberService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service = new MemberService(fhir.client(), new MemberMapper());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  "})
  void searchWithoutFiltersListsOnlyMembersWithTheirCoverage(String blank) {
    Bundle bundle =
        bundleOf(
            member("m1", "PHP-10001"),
            member("m2", "PHP-10002"),
            coverage("c1", "m1", Coverage.CoverageStatus.ACTIVE, "2025-11-09", null));
    bundle.setTotal(2);
    fhir.givenSearchResult(Patient.class, bundle);

    PageResponse<MemberSummary> page = service.searchMembers(blank, blank);

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items())
        .extracting(MemberSummary::id, MemberSummary::planName, MemberSummary::coverageStatus)
        .containsExactly(tuple("m1", GOLD_PLAN_NAME, "active"), tuple("m2", null, null));
    assertThat(fhir.lastSearchCriteria(Patient.class))
        .containsExactly(
            MEMBER_IDS, "_revinclude=Coverage:beneficiary", "_count=" + MemberService.MAX_RESULTS);
  }

  @Test
  void searchFiltersByMemberIdAndName() {
    service.searchMembers(" Nair ", " PHP-10001 ");

    assertThat(fhir.lastSearchCriteria(Patient.class))
        .containsExactly(
            MEMBER_IDS + "PHP-10001",
            "_revinclude=Coverage:beneficiary",
            "_count=" + MemberService.MAX_RESULTS,
            "name=Nair");
  }

  @Test
  void memberDetailHasCoveragePlanAndPriorAuthHistory() {
    fhir.givenStored(member("m1", "PHP-10001"), "m1");
    fhir.givenSearchResult(
        Coverage.class,
        bundleOf(coverage("c1", "m1", Coverage.CoverageStatus.ACTIVE, "2025-11-09", null)));
    fhir.givenSearchResult(InsurancePlan.class, bundleOf(goldPlan()));
    fhir.givenSearchResult(
        Claim.class,
        bundleOf(
            preAuth("pa2", "m1", "2026-11-02"),
            preAuth("pa1", "m1", "2026-11-01"),
            decision("cr1", "pa1")));

    MemberResponse member = service.getMember("m1");

    assertThat(member.memberId()).isEqualTo("PHP-10001");
    assertThat(member.coverages())
        .singleElement()
        .satisfies(
            coverage ->
                assertThat(coverage.plan())
                    .isEqualTo(new PlanSummary("plan-gold", GOLD_PLAN_ID, GOLD_PLAN_NAME, "PPO")));
    assertThat(member.priorAuths())
        .extracting(PriorAuthSummary::id, PriorAuthSummary::outcome)
        .containsExactly(tuple("pa2", null), tuple("pa1", "complete"));
    assertThat(member.priorAuths().get(1).submitted()).isEqualTo(LocalDate.of(2026, 11, 1));
    assertThat(fhir.lastSearchCriteria(Coverage.class))
        .containsExactly("beneficiary=Patient/m1", "_count=" + MemberService.MAX_RESULTS);
    assertThat(fhir.lastSearchCriteria(InsurancePlan.class))
        .containsExactly("identifier=" + MemberMapper.INSURANCE_PLAN_SYSTEM + "|" + GOLD_PLAN_ID);
    assertThat(fhir.lastSearchCriteria(Claim.class))
        .containsExactly(
            "patient=Patient/m1",
            "use=preauthorization",
            "_revinclude=ClaimResponse:request",
            "_sort=-created",
            "_count=" + MemberService.MAX_RESULTS);
  }

  @Test
  void memberWithoutPriorAuthsOrCoverageHasEmptyListsAndSkipsThePlanLookup() {
    fhir.givenStored(member("m1", "PHP-10001"), "m1");

    MemberResponse member = service.getMember("m1");

    assertThat(member.coverages()).isEmpty();
    assertThat(member.priorAuths()).isEmpty();
    assertThat(fhir.searchCount(Claim.class)).isEqualTo(1);
    assertThat(fhir.searchCount(InsurancePlan.class)).isZero();
  }

  @Test
  void unknownMemberIsNotFound() {
    assertThatThrownBy(() -> service.getMember("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Member with id 'missing' not found");
  }

  @Test
  void patientWithoutMemberIdIsNotAMember() {
    fhir.givenStored(member("p1", null), "p1");

    assertThatThrownBy(() -> service.getMember("p1")).isInstanceOf(ResourceNotFoundException.class);
  }
}
