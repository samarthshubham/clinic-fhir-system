package io.github.samarthshubham.payer.mapper;

import static io.github.samarthshubham.payer.support.MemberFixtures.GOLD_PLAN_ID;
import static io.github.samarthshubham.payer.support.MemberFixtures.GOLD_PLAN_NAME;
import static io.github.samarthshubham.payer.support.MemberFixtures.coverage;
import static io.github.samarthshubham.payer.support.MemberFixtures.decision;
import static io.github.samarthshubham.payer.support.MemberFixtures.goldPlan;
import static io.github.samarthshubham.payer.support.MemberFixtures.member;
import static io.github.samarthshubham.payer.support.MemberFixtures.preAuth;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.samarthshubham.payer.dto.MemberCoverage;
import io.github.samarthshubham.payer.dto.MemberResponse;
import io.github.samarthshubham.payer.dto.MemberSummary;
import io.github.samarthshubham.payer.dto.PlanSummary;
import io.github.samarthshubham.payer.dto.PriorAuthSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.InsurancePlan;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class MemberMapperTest {

  private final MemberMapper mapper = new MemberMapper();

  @Test
  void summaryShowsTheActiveCoverageEvenWhenAnEndedOneIsNewer() {
    Coverage active = coverage("c1", "m1", Coverage.CoverageStatus.ACTIVE, "2025-11-09", null);
    Coverage ended =
        coverage("c2", "m1", Coverage.CoverageStatus.CANCELLED, "2026-01-01", "2026-02-01");
    ended.getClass_().get(1).setName("Praxis Silver HMO");

    MemberSummary summary = mapper.toSummary(member("m1", "PHP-10001"), List.of(ended, active));

    assertThat(summary)
        .isEqualTo(
            new MemberSummary(
                "m1",
                "PHP-10001",
                "Priya",
                "Nair",
                LocalDate.of(1985, 3, 14),
                "female",
                GOLD_PLAN_NAME,
                "active"));
  }

  @Test
  void summaryFallsBackToTheNewestCoverageWhenNoneIsActive() {
    Coverage older =
        coverage("c1", "m1", Coverage.CoverageStatus.CANCELLED, "2024-01-01", "2024-12-31");
    older.getClass_().get(1).setName("Old plan");
    Coverage newer =
        coverage("c2", "m1", Coverage.CoverageStatus.CANCELLED, "2025-01-01", "2025-10-10");

    MemberSummary summary = mapper.toSummary(member("m1", "PHP-10008"), List.of(older, newer));

    assertThat(summary.planName()).isEqualTo(GOLD_PLAN_NAME);
    assertThat(summary.coverageStatus()).isEqualTo("cancelled");
  }

  @Test
  void summaryOfAnUncoveredMemberWithSparseDemographicsHasNulls() {
    Patient patient = new Patient();
    patient.setId("m2");
    patient.setBirthDateElement(new DateType("1985"));

    MemberSummary summary = mapper.toSummary(patient, List.of());

    assertThat(summary)
        .isEqualTo(new MemberSummary("m2", null, null, null, null, null, null, null));
  }

  @Test
  void responseListsCoveragesNewestFirstWithTheirPlanAndPriorAuths() {
    Coverage active = coverage("c1", "m1", Coverage.CoverageStatus.ACTIVE, "2025-11-09", null);
    Coverage ended =
        coverage("c0", "m1", Coverage.CoverageStatus.CANCELLED, "2024-01-01", "2024-12-31");
    PriorAuthSummary priorAuth =
        new PriorAuthSummary("pa1", "active", LocalDate.of(2026, 11, 1), "Echo", null, null);

    MemberResponse response =
        mapper.toResponse(
            member("m1", "PHP-10001"),
            List.of(ended, active),
            Map.of(GOLD_PLAN_ID, goldPlan()),
            List.of(priorAuth));

    PlanSummary gold = new PlanSummary("plan-gold", GOLD_PLAN_ID, GOLD_PLAN_NAME, "PPO");
    assertThat(response)
        .isEqualTo(
            new MemberResponse(
                "m1",
                "PHP-10001",
                "Priya",
                "Nair",
                LocalDate.of(1985, 3, 14),
                "female",
                List.of(
                    new MemberCoverage(
                        "c1",
                        "active",
                        LocalDate.of(2025, 11, 9),
                        null,
                        "Praxis Health Plan",
                        "self",
                        gold),
                    new MemberCoverage(
                        "c0",
                        "cancelled",
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2024, 12, 31),
                        "Praxis Health Plan",
                        "self",
                        gold)),
                List.of(priorAuth)));
  }

  @Test
  void coverageWhosePlanIsNotInThePayerHapiKeepsThePlanFromTheCoverageClass() {
    Coverage active = coverage("c1", "m1", Coverage.CoverageStatus.ACTIVE, "2025-11-09", null);

    MemberResponse response =
        mapper.toResponse(member("m1", "PHP-10001"), List.of(active), Map.of(), List.of());

    assertThat(response.coverages().getFirst().plan())
        .isEqualTo(new PlanSummary(null, GOLD_PLAN_ID, GOLD_PLAN_NAME, null));
  }

  @Test
  void coverageWithoutPlanClassPayerOrStatusHasNoPlan() {
    Coverage bare = new Coverage();
    bare.setId("c9");

    MemberResponse response =
        mapper.toResponse(member("m1", "PHP-10001"), List.of(bare), Map.of(), List.of());

    assertThat(response.coverages())
        .containsExactly(new MemberCoverage("c9", null, null, null, null, null, null));
  }

  @Test
  void planWithoutNameUsesTheCoverageClassName() {
    InsurancePlan plan = goldPlan();
    plan.setName(null);

    MemberResponse response =
        mapper.toResponse(
            member("m1", "PHP-10001"),
            List.of(coverage("c1", "m1", Coverage.CoverageStatus.ACTIVE, "2025-11-09", null)),
            Map.of(GOLD_PLAN_ID, plan),
            List.of());

    assertThat(response.coverages().getFirst().plan().name()).isEqualTo(GOLD_PLAN_NAME);
  }

  @Test
  void priorAuthTakesTheDecisionFromItsClaimResponse() {
    PriorAuthSummary priorAuth =
        mapper.toPriorAuth(
            preAuth("pa1", "m1", "2026-11-01T10:15:00-05:00"), decision("cr1", "pa1"));

    assertThat(priorAuth)
        .isEqualTo(
            new PriorAuthSummary(
                "pa1",
                "active",
                LocalDate.of(2026, 11, 1),
                "Echocardiography",
                "complete",
                "PA-0001"));
  }

  @Test
  void undecidedPriorAuthHasNoOutcome() {
    Claim claim = preAuth("pa1", "m1", "2026-11-01");
    claim.getItemFirstRep().getProductOrService().setText("Echo (resting)");

    PriorAuthSummary priorAuth = mapper.toPriorAuth(claim, null);

    assertThat(priorAuth.service()).isEqualTo("Echo (resting)");
    assertThat(priorAuth.outcome()).isNull();
    assertThat(priorAuth.preAuthRef()).isNull();
  }

  @Test
  void priorAuthServiceFallsBackToTheCode() {
    Claim claim = new Claim();
    claim.setId("pa2");
    claim.addItem().getProductOrService().addCoding().setCode("E2103");

    PriorAuthSummary priorAuth = mapper.toPriorAuth(claim, null);

    assertThat(priorAuth).isEqualTo(new PriorAuthSummary("pa2", null, null, "E2103", null, null));
  }

  @Test
  void planIdComesFromThePlanIdentifier() {
    assertThat(MemberMapper.planId(goldPlan())).isEqualTo(GOLD_PLAN_ID);
    assertThat(MemberMapper.planId(new InsurancePlan())).isNull();
  }
}
