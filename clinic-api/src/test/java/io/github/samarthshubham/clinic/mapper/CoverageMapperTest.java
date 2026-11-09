package io.github.samarthshubham.clinic.mapper;

import static io.github.samarthshubham.clinic.support.GoldenFhir.assertMatchesGolden;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.samarthshubham.clinic.dto.CoverageRequest;
import io.github.samarthshubham.clinic.dto.CoverageResponse;
import java.time.LocalDate;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

class CoverageMapperTest {

  private static final LocalDate START = LocalDate.of(2026, 1, 1);
  private static final LocalDate END = LocalDate.of(2026, 12, 31);
  private static final CoverageRequest PRIYA =
      new CoverageRequest(
          "seed-priya-nair",
          "praxis-health-plan",
          "Praxis Health Plan",
          "PHP-10001",
          "Praxis Gold PPO",
          "active",
          START,
          END,
          null);

  private final CoverageMapper mapper = new CoverageMapper();

  @Test
  void mapsMemberBeneficiaryPayerPlanAndPeriod() {
    Coverage coverage = mapper.toFhir(PRIYA, "org1");

    assertThat(coverage.getMeta().hasProfile(UsCoreProfiles.COVERAGE)).isTrue();
    assertThat(coverage.getIdentifierFirstRep().getSystem())
        .isEqualTo(CoverageMapper.MEMBER_ID_SYSTEM);
    assertThat(coverage.getIdentifierFirstRep().getValue()).isEqualTo("PHP-10001");
    assertThat(coverage.getIdentifierFirstRep().getType().getCodingFirstRep().getCode())
        .isEqualTo("MB");
    assertThat(coverage.getSubscriberId()).isEqualTo("PHP-10001");
    assertThat(coverage.getStatus()).isEqualTo(Coverage.CoverageStatus.ACTIVE);
    assertThat(coverage.getBeneficiary().getReference()).isEqualTo("Patient/seed-priya-nair");
    assertThat(coverage.getRelationship().getCodingFirstRep().getCode()).isEqualTo("self");
    assertThat(coverage.getPeriod().getStartElement().getValueAsString()).isEqualTo("2026-01-01");
    assertThat(coverage.getPeriod().getEndElement().getValueAsString()).isEqualTo("2026-12-31");
    Reference payor = coverage.getPayorFirstRep();
    assertThat(payor.getReference()).isEqualTo("Organization/org1");
    assertThat(payor.getDisplay()).isEqualTo("Praxis Health Plan");
    assertThat(payor.getIdentifier().getValue()).isEqualTo("praxis-health-plan");
    assertThat(coverage.getClass_FirstRep().getName()).isEqualTo("Praxis Gold PPO");
  }

  @Test
  void omitsPlanAndPeriodEndWhenNotGivenAndKeepsAnExplicitRelationship() {
    Coverage coverage =
        mapper.toFhir(
            new CoverageRequest(
                "p1",
                "praxis-health-plan",
                "Praxis Health Plan",
                "PHP-10002",
                null,
                "cancelled",
                START,
                null,
                "spouse"),
            "org1");

    assertThat(coverage.hasClass_()).isFalse();
    assertThat(coverage.getPeriod().hasEnd()).isFalse();
    assertThat(coverage.getStatus()).isEqualTo(Coverage.CoverageStatus.CANCELLED);
    assertThat(coverage.getRelationship().getCodingFirstRep().getCode()).isEqualTo("spouse");
  }

  @Test
  void unknownStatusIsRejected() {
    CoverageRequest request =
        new CoverageRequest("p1", "x", "X", "PHP-10003", null, "lapsed", START, null, null);

    assertThatThrownBy(() -> mapper.toFhir(request, "org1")).isInstanceOf(FHIRException.class);
  }

  @Test
  void payerOrganizationIsAnActiveUsCorePayerWithItsIdentifier() {
    Organization payer = mapper.toPayerOrganization(PRIYA);

    assertThat(payer.getMeta().hasProfile(UsCoreProfiles.ORGANIZATION)).isTrue();
    assertThat(payer.getIdentifierFirstRep().getSystem()).isEqualTo(CoverageMapper.PAYER_SYSTEM);
    assertThat(payer.getIdentifierFirstRep().getValue()).isEqualTo("praxis-health-plan");
    assertThat(payer.getActive()).isTrue();
    assertThat(payer.getName()).isEqualTo("Praxis Health Plan");
    assertThat(payer.getTypeFirstRep().getCodingFirstRep().getCode()).isEqualTo("pay");
  }

  @Test
  void toResponseIsTheReverseOfToFhir() {
    Coverage coverage = mapper.toFhir(PRIYA, "org1");
    coverage.setId("cov1");

    assertThat(mapper.toResponse(coverage))
        .isEqualTo(
            new CoverageResponse(
                "cov1",
                "seed-priya-nair",
                "org1",
                "praxis-health-plan",
                "Praxis Health Plan",
                "PHP-10001",
                "Praxis Gold PPO",
                "active",
                START,
                END,
                "self"));
  }

  @Test
  void toResponseFallsBackToSubscriberIdAndIgnoresNonPlanClasses() {
    Coverage coverage = new Coverage().setSubscriberId("PHP-10004");
    coverage.setId("cov1");
    coverage.addClass_().setValue("G1").setName("Group 1").getType().addCoding().setCode("group");

    CoverageResponse response = mapper.toResponse(coverage);

    assertThat(response.memberId()).isEqualTo("PHP-10004");
    assertThat(response.planName()).isNull();
  }

  @Test
  void toResponseLeavesMissingFieldsNull() {
    Coverage coverage = new Coverage();
    coverage.setId("cov1");

    assertThat(mapper.toResponse(coverage))
        .isEqualTo(
            new CoverageResponse(
                "cov1", null, null, null, null, null, null, null, null, null, null));
  }

  @Test
  void coverageMatchesTheGoldenFixture() {
    assertMatchesGolden(mapper.toFhir(PRIYA, "seed-praxis-health-plan"), "provider/coverage.json");
  }

  @Test
  void payerOrganizationMatchesTheGoldenFixture() {
    assertMatchesGolden(mapper.toPayerOrganization(PRIYA), "provider/organization-payer.json");
  }
}
