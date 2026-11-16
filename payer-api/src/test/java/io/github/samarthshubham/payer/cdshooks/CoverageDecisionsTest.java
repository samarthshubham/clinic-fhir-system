package io.github.samarthshubham.payer.cdshooks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.samarthshubham.payer.service.CoverageRuleService;
import io.github.samarthshubham.payer.service.MemberCoverageCheck;
import java.time.LocalDate;
import java.util.List;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

class CoverageDecisionsTest {

  private static final MemberCoverageCheck ACTIVE =
      new MemberCoverageCheck(MemberCoverageCheck.Status.ACTIVE, "payer-coverage");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

  private final CoverageDecisions decisions =
      new CoverageDecisions(new CoverageRuleService(new ClassPathResource("coverage-rules.yaml")));

  @ParameterizedTest(name = "{0}|{1} {2} on {3}: PA {4}, docs {5}")
  @CsvSource({
    "http://loinc.org, 58410-2, routine, ORDER_SIGN, false, false",
    "http://www.ama-assn.org/go/cpt, 72148, routine, ORDER_SIGN, true, true",
    "http://www.ama-assn.org/go/cpt, 72148, stat, ORDER_SIGN, true, true",
    "http://www.ama-assn.org/go/cpt, 72148, routine, ORDER_SELECT, true, false",
    "http://snomed.info/sct, 76746007, routine, ORDER_SIGN, true, false",
    "http://snomed.info/sct, 276341003, urgent, ORDER_SIGN, true, false",
    "http://snomed.info/sct, 276341003, stat, ORDER_SIGN, false, false",
    "http://example.org/unknown, 123, routine, ORDER_SIGN, false, false",
  })
  void decisionsComeFromTheCoverageRules(
      String system,
      String code,
      String priority,
      CrdHook hook,
      boolean priorAuthNeeded,
      boolean documentationNeeded) {
    ServiceRequest order = order(system, code);
    order.setPriority(ServiceRequest.ServiceRequestPriority.fromCode(priority));

    CoverageDecision decision = decisions.decide(hook, order, ACTIVE);

    assertThat(decision.priorAuthNeeded()).isEqualTo(priorAuthNeeded);
    assertThat(decision.documentationNeeded()).isEqualTo(documentationNeeded);
    assertThat(decision.questionnaire() != null).isEqualTo(documentationNeeded);
  }

  @Test
  void aKnownCodingIsPreferredOverTheFirstOne() {
    ServiceRequest order = order("http://example.org/local", "MRI-LS");
    order.getCode().addCoding().setSystem("http://www.ama-assn.org/go/cpt").setCode("72148");

    CoverageDecision decision = decisions.decide(CrdHook.ORDER_SIGN, order, ACTIVE);

    assertThat(decision.priorAuthNeeded()).isTrue();
    assertThat(decision.serviceName()).isEqualTo("MRI lumbar spine without contrast");
  }

  @Test
  void emergencyWaiverIsExplainedOnTheCard() {
    ServiceRequest order = order("http://snomed.info/sct", "276341003");
    order.setPriority(ServiceRequest.ServiceRequestPriority.STAT);

    CoverageDecision decision = decisions.decide(CrdHook.ORDER_SIGN, order, ACTIVE);

    assertThat(decision.priorAuthWaived()).isTrue();
    assertThat(decision.summary()).startsWith("Covered, no prior authorization needed: ");
    assertThat(decision.indicator()).isEqualTo("info");
    assertThat(decision.detail("Praxis Health Plan", TODAY)).contains("waived for stat");
  }

  @Test
  void unknownCodesAreNamedFromTheOrder() {
    ServiceRequest withText = order("http://example.org/unknown", "1");
    withText.getCode().setText("Foot X-ray");
    ServiceRequest withDisplay = order("http://example.org/unknown", "1");
    withDisplay.getCode().getCodingFirstRep().setDisplay("Hand X-ray");
    ServiceRequest withCodeOnly = order("http://example.org/unknown", "XR-1");
    ServiceRequest withoutCode = new ServiceRequest();

    assertThat(
            List.of(withText, withDisplay, withCodeOnly, withoutCode).stream()
                .map(order -> decisions.decide(CrdHook.ORDER_SIGN, order, ACTIVE).serviceName()))
        .containsExactly("Foot X-ray", "Hand X-ray", "XR-1", "this order");
  }

  @Test
  void unverifiedMembersAreNotCoveredWithAReasonAndNoPaNeeded() {
    ServiceRequest order = order("http://www.ama-assn.org/go/cpt", "72148");

    for (MemberCoverageCheck.Status status :
        List.of(
            MemberCoverageCheck.Status.NO_ACTIVE_COVERAGE,
            MemberCoverageCheck.Status.NO_MEMBER_FOUND)) {
      CoverageDecision decision =
          decisions.decide(CrdHook.ORDER_SIGN, order, new MemberCoverageCheck(status, null));
      Extension information =
          decisions.coverageInformation(decision, "Coverage/c1", TODAY, "assertion-1");

      assertThat(decision.summary()).isEqualTo("Coverage could not be verified");
      assertThat(decision.indicator()).isEqualTo("warning");
      assertThat(code(information, "covered")).isEqualTo("not-covered");
      assertThat(information.getExtensionByUrl("pa-needed")).isNull();
      assertThat(
              ((CodeableConcept) information.getExtensionByUrl("reason").getValue())
                  .getCodingFirstRep()
                  .getCode())
          .isEqualTo(
              status == MemberCoverageCheck.Status.NO_MEMBER_FOUND
                  ? "no-member-found"
                  : "no-active-coverage");
    }
  }

  @Test
  void unverifiedDetailsSayWhatIsMissing() {
    CoverageDecision noMember =
        new CoverageDecision(
            MemberCoverageCheck.Status.NO_MEMBER_FOUND, "MRI", false, false, false, null);
    CoverageDecision noCoverage =
        new CoverageDecision(
            MemberCoverageCheck.Status.NO_ACTIVE_COVERAGE, "MRI", false, false, false, null);

    assertThat(noMember.detail("Praxis Health Plan", TODAY)).contains("has no member");
    assertThat(noCoverage.detail("Praxis Health Plan", TODAY))
        .contains("no active coverage for this member on 2026-11-11");
  }

  @Test
  void paWithoutDocumentationHasAShortDetail() {
    CoverageDecision decision =
        new CoverageDecision(
            MemberCoverageCheck.Status.ACTIVE, "Stress test", true, false, false, null);

    assertThat(decision.detail("Praxis Health Plan", TODAY))
        .isEqualTo("Praxis Health Plan needs prior authorization for this order.");
  }

  @Test
  void longSummariesAreCutTo140Characters() {
    CoverageDecision decision =
        new CoverageDecision(
            MemberCoverageCheck.Status.ACTIVE, "x".repeat(200), true, false, false, null);

    assertThat(decision.summary()).hasSize(140).endsWith("…");
  }

  @Test
  void stableIdsDependOnTheHookInstanceAndTheOrder() {
    assertThat(CoverageDecisions.stableId("h1", "ServiceRequest/a"))
        .isEqualTo(CoverageDecisions.stableId("h1", "ServiceRequest/a"))
        .isNotEqualTo(CoverageDecisions.stableId("h2", "ServiceRequest/a"))
        .isNotEqualTo(CoverageDecisions.stableId("h1", "ServiceRequest/b"));
  }

  private static ServiceRequest order(String system, String code) {
    ServiceRequest order = new ServiceRequest();
    order.setId("draft-1");
    order.getCode().addCoding().setSystem(system).setCode(code);
    return order;
  }

  private static String code(Extension information, String url) {
    return ((CodeType) information.getExtensionByUrl(url).getValue()).getValue();
  }
}
