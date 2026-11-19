package io.github.samarthshubham.payer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.samarthshubham.payer.service.PriorAuthDecision.Outcome;
import java.util.HashMap;
import java.util.Map;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

class PriorAuthAdjudicatorTest {

  private static final String CPT = "http://www.ama-assn.org/go/cpt";
  private static final String HCPCS = "http://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets";
  private static final String SNOMED = "http://snomed.info/sct";
  private static final String LOINC = "http://loinc.org";
  private static final MemberCoverageCheck ACTIVE =
      new MemberCoverageCheck(MemberCoverageCheck.Status.ACTIVE, "payer-coverage");

  private final PriorAuthAdjudicator adjudicator =
      new PriorAuthAdjudicator(
          new CoverageRuleService(new ClassPathResource("coverage-rules.yaml")));

  @ParameterizedTest(name = "{0}: {6}")
  @CsvSource(
      delimiter = '|',
      value = {
        "Priya Nair, CBC                   | http://loinc.org | 58410-2   | routine | ''                                                     | ACTIVE  | NOT_REQUIRED | ''",
        "Ramon Castillo, CGM               | HCPCS            | E2103     | routine | diabetes-diagnosis=true;insulin-treated=true           | ACTIVE  | APPROVED     | ''",
        "Ramon Castillo, CGM no insulin    | HCPCS            | E2103     | routine | diabetes-diagnosis=true;insulin-treated=false          | ACTIVE  | PENDED       | ''",
        "Gerald Horton, polysomnography    | SNOMED           | 60554003  | routine | ''                                                     | ACTIVE  | PENDED       | ''",
        "Marcus Webb, echocardiogram       | SNOMED           | 40701008  | urgent  | ''                                                     | ACTIVE  | PENDED       | ''",
        "Sandra Okafor, MRI under 6 weeks  | CPT              | 72148     | routine | conservative-therapy-weeks=4;neurological-deficit=false | ACTIVE  | DENIED       | conservative-therapy-not-met",
        "MRI with a neurological deficit   | CPT              | 72148     | routine | conservative-therapy-weeks=6;neurological-deficit=true  | ACTIVE  | APPROVED     | ''",
        "MRI without a deficit             | CPT              | 72148     | routine | conservative-therapy-weeks=8;neurological-deficit=false | ACTIVE  | PENDED       | ''",
        "MRI without answers               | CPT              | 72148     | routine | ''                                                     | ACTIVE  | PENDED       | ''",
        "Tony Stark, stress test           | SNOMED           | 76746007  | routine | ''                                                     | ACTIVE  | PENDED       | ''",
        "Polysomnography claim line (CPT)  | CPT              | 95810     | routine | ''                                                     | ACTIVE  | PENDED       | ''",
        "Echocardiogram claim line (CPT)   | CPT              | 93306     | urgent  | ''                                                     | ACTIVE  | PENDED       | ''",
        "Stress test claim line (CPT)      | CPT              | 93015     | routine | ''                                                     | ACTIVE  | PENDED       | ''",
        "Robert Osei, emergency stat       | SNOMED           | 276341003 | stat    | ''                                                     | ACTIVE  | NOT_REQUIRED | ''",
        "Emergency workup, urgent          | SNOMED           | 276341003 | urgent  | ''                                                     | ACTIVE  | PENDED       | ''",
        "Aisha Chen, inactive coverage     | CPT              | 72148     | routine | ''                                                     | NO_ACTIVE_COVERAGE | DENIED | no-active-coverage",
        "Aisha Chen, member not found      | CPT              | 72148     | routine | ''                                                     | NO_MEMBER_FOUND    | DENIED | no-member-found",
        "Unknown code                      | http://example.org/codes | 123 | routine | ''                                                | ACTIVE  | NOT_REQUIRED | ''",
      })
  void adjudicatesEveryDemoOutcome(
      String scenario,
      String system,
      String code,
      String priority,
      String answers,
      MemberCoverageCheck.Status coverage,
      Outcome outcome,
      String denialCode) {
    PriorAuthDecision decision =
        adjudicator.adjudicate(
            service(system, code),
            OrderPriority.fromCode(priority),
            new MemberCoverageCheck(coverage, null),
            answers(answers));

    assertThat(decision.outcome()).isEqualTo(outcome);
    if (denialCode.isEmpty()) {
      assertThat(decision.denialReason()).isNull();
    } else {
      assertThat(decision.denialReason().code()).isEqualTo(denialCode);
      assertThat(decision.denialReason().text()).isNotBlank();
    }
  }

  @Test
  void usesTheFirstCodingThatHasARule() {
    CodeableConcept service =
        new CodeableConcept()
            .addCoding(new Coding(SNOMED, "60554003", null))
            .addCoding(new Coding(CPT, "95810", null));

    assertThat(adjudicator.adjudicate(service, OrderPriority.ROUTINE, ACTIVE, Map.of()).outcome())
        .isEqualTo(Outcome.PENDED);
  }

  @Test
  void aDenialWithoutAReasonCannotBeMade() {
    assertThatThrownBy(() -> new PriorAuthDecision(Outcome.DENIED, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason");
  }

  private static CodeableConcept service(String system, String code) {
    String resolved =
        switch (system) {
          case "CPT" -> CPT;
          case "HCPCS" -> HCPCS;
          case "SNOMED" -> SNOMED;
          case "LOINC" -> LOINC;
          default -> system;
        };
    return new CodeableConcept(new Coding(resolved, code, null));
  }

  private static Map<String, String> answers(String answers) {
    Map<String, String> byLinkId = new HashMap<>();
    if (answers.isBlank()) {
      return byLinkId;
    }
    for (String answer : answers.split(";")) {
      String[] parts = answer.split("=", 2);
      byLinkId.put(parts[0], parts[1]);
    }
    return byLinkId;
  }
}
