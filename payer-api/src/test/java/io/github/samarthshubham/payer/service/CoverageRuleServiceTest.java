package io.github.samarthshubham.payer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class CoverageRuleServiceTest {

  private static final String LOINC = "http://loinc.org";
  private static final String SNOMED = "http://snomed.info/sct";
  private static final String HCPCS = "http://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets";
  private static final String CPT = "http://www.ama-assn.org/go/cpt";
  private static final String QUESTIONNAIRE_BASE =
      "http://praxishealthplan.example/fhir/Questionnaire/";

  private final CoverageRuleService service =
      new CoverageRuleService(new ClassPathResource("coverage-rules.yaml"));

  static Stream<Arguments> demoScenarios() {
    return Stream.of(
        Arguments.of("Priya Nair CBC", LOINC, "58410-2", "routine", false, false, null),
        Arguments.of("Priya Nair thyroid panel", LOINC, "24348-5", "routine", false, false, null),
        Arguments.of(
            "Ramon Castillo CGM",
            HCPCS,
            "E2103",
            "routine",
            true,
            true,
            QUESTIONNAIRE_BASE + "continuous-glucose-monitor"),
        Arguments.of(
            "Gerald Horton polysomnography",
            SNOMED,
            "60554003",
            "routine",
            true,
            true,
            QUESTIONNAIRE_BASE + "polysomnography"),
        Arguments.of(
            "Marcus Webb echocardiogram, urgent",
            SNOMED,
            "40701008",
            "urgent",
            true,
            true,
            QUESTIONNAIRE_BASE + "echocardiogram"),
        Arguments.of(
            "Sandra Okafor MRI lumbar spine",
            CPT,
            "72148",
            "routine",
            true,
            true,
            QUESTIONNAIRE_BASE + "mri-lumbar-spine"),
        Arguments.of(
            "Tony Stark cardiac stress test", SNOMED, "76746007", "routine", true, false, null),
        Arguments.of(
            "Robert Osei emergency cardiac workup, stat",
            SNOMED,
            "276341003",
            "stat",
            false,
            false,
            null),
        Arguments.of("cardiac workup, not stat", SNOMED, "276341003", "urgent", true, false, null),
        Arguments.of("unknown code", LOINC, "2345-7", "routine", false, false, null));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("demoScenarios")
  void answersEachDemoScenario(
      String scenario,
      String system,
      String code,
      String priority,
      boolean priorAuthRequired,
      boolean docNeeded,
      String questionnaire) {
    CoverageRule rule = service.findRule(system, code);

    assertThat(rule.priorAuthRequired(OrderPriority.fromCode(priority)))
        .isEqualTo(priorAuthRequired);
    assertThat(rule.docNeeded()).isEqualTo(docNeeded);
    assertThat(rule.questionnaire()).isEqualTo(questionnaire);
  }

  @Test
  void onlyTheEmergencyWorkupIsEmergencyExempt() {
    assertThat(service.findRule(SNOMED, "276341003").emergencyExempt()).isTrue();
    assertThat(service.findRule(SNOMED, "40701008").priorAuthRequired(OrderPriority.STAT)).isTrue();
  }

  @Test
  void missingPriorityIsNotAnEmergency() {
    assertThat(service.findRule(SNOMED, "276341003").priorAuthRequired(null)).isTrue();
  }

  @Test
  void unknownCodeIsLookedUpBySystemAndCode() {
    CoverageRule rule = service.findRule(SNOMED, "58410-2");

    assertThat(rule.paNeeded()).isFalse();
    assertThat(rule.autoApproveCriteria()).isEmpty();
  }

  @Test
  void cgmCriteriaAreMetByRamonCastillosAnswers() {
    Map<String, String> answers = Map.of("diabetes-diagnosis", "true", "insulin-treated", "true");

    List<ApprovalCriterion> criteria = service.findRule(HCPCS, "E2103").autoApproveCriteria();

    assertThat(criteria).isNotEmpty();
    assertThat(criteria).allMatch(criterion -> criterion.isMetBy(answers.get(criterion.linkId())));
  }

  @Test
  void mriIsDeniedForSandraOkaforWithUnderSixWeeksOfConservativeTherapy() {
    ApprovalCriterion therapy =
        criterion(service.findRule(CPT, "72148"), "conservative-therapy-weeks");

    assertThat(therapy.isMetBy("4")).isFalse();
    assertThat(therapy.isMetBy("6")).isTrue();
    assertThat(therapy.denialReason().code()).isEqualTo("conservative-therapy-not-met");
    assertThat(therapy.denialReason().text()).contains("Under 6 weeks of conservative therapy");
  }

  @Test
  void rulesWithoutCriteriaAlwaysGoToReview() {
    assertThat(service.findRule(SNOMED, "60554003").autoApproveCriteria()).isEmpty();
    assertThat(service.findRule(SNOMED, "40701008").autoApproveCriteria()).isEmpty();
    assertThat(service.findRule(SNOMED, "76746007").autoApproveCriteria()).isEmpty();
  }

  static Stream<Arguments> invalidFiles() {
    String valid =
        """
        rules:
          - system: http://loinc.org
            code: 58410-2
            paNeeded: false
            docNeeded: false
            emergencyExempt: false
        """;
    return Stream.of(
        Arguments.of("rules: [", "while parsing"),
        Arguments.of("", "the file has no rules"),
        Arguments.of(
            valid.replace("docNeeded: false", "docNeeded: false\n    paNeeded: true"),
            "found duplicate key paNeeded"),
        Arguments.of("rules: []", "the file has no rules"),
        Arguments.of("rules:\n  -", "rule 1 is empty"),
        Arguments.of(
            valid.replace("paNeeded", "paNeded"),
            "rules[1].paNeded: Unrecognized field \"paNeded\""),
        Arguments.of(
            valid.replace("    paNeeded: false\n", ""),
            "paNeeded, docNeeded and emergencyExempt are required"),
        Arguments.of(
            valid + "    autoApproveCriteria:\n      -\n",
            "autoApproveCriteria has an empty criterion"),
        Arguments.of(valid.replace("http://loinc.org", "loinc"), "system must be an absolute URI"),
        Arguments.of(valid.replace("58410-2", "\"\""), "code is required"),
        Arguments.of(valid + valid.replace("rules:\n", ""), "rule 2 (http://loinc.org|58410-2)"),
        Arguments.of(
            valid.replace("docNeeded: false", "docNeeded: true"), "docNeeded needs paNeeded"),
        Arguments.of(
            valid
                .replace("paNeeded: false", "paNeeded: true")
                .replace("docNeeded: false", "docNeeded: true"),
            "docNeeded needs a questionnaire canonical URL"),
        Arguments.of(
            valid.replace(
                "docNeeded: false", "docNeeded: false\n    questionnaire: http://q.example"),
            "questionnaire is only allowed when docNeeded"),
        Arguments.of(
            valid.replace("emergencyExempt: false", "emergencyExempt: true"),
            "emergencyExempt needs paNeeded"),
        Arguments.of(
            valid
                + "    autoApproveCriteria:\n"
                + "      - linkId: weeks\n"
                + "        operator: AT_LEAST\n"
                + "        value: six\n",
            "criterion 'weeks' needs a numeric value for AT_LEAST"),
        Arguments.of(
            valid + "    autoApproveCriteria:\n      - linkId: weeks\n",
            "criterion 'weeks' needs an operator"),
        Arguments.of(
            valid
                + "    autoApproveCriteria:\n"
                + "      - operator: EQUALS\n"
                + "        value: \"true\"\n"
                + "        denialReason:\n"
                + "          code: no-text\n",
            "criterion linkId is required"),
        Arguments.of(
            valid + "    autoApproveCriteria:\n      - linkId: weeks\n        operator: EQUALS\n",
            "criterion 'weeks' needs a value"),
        Arguments.of(
            valid
                + "    autoApproveCriteria:\n"
                + "      - linkId: weeks\n"
                + "        operator: SOMETIMES\n"
                + "        value: \"6\"\n",
            "SOMETIMES"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("invalidFiles")
  void invalidFileIsRejectedWithAClearMessage(String yaml, String expectedMessage) {
    ByteArrayResource file =
        new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8), "test rules");

    assertThatThrownBy(() -> new CoverageRuleService(file))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Invalid coverage rules in Byte array resource [test rules]: ")
        .hasMessageContaining(expectedMessage);
  }

  @Test
  void criteriaWithoutDocumentationAreRejected() {
    String yaml =
        """
        rules:
          - system: http://snomed.info/sct
            code: "76746007"
            paNeeded: true
            docNeeded: false
            emergencyExempt: false
            autoApproveCriteria:
              - linkId: weeks
                operator: AT_LEAST
                value: "6"
                denialReason:
                  code: too-soon
        """;

    assertThatThrownBy(
            () ->
                new CoverageRuleService(
                    new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8))))
        .hasMessageContaining("autoApproveCriteria need docNeeded")
        .hasMessageContaining("criterion 'weeks' denialReason needs a code and a text");
  }

  private static ApprovalCriterion criterion(CoverageRule rule, String linkId) {
    return rule.autoApproveCriteria().stream()
        .filter(criterion -> criterion.linkId().equals(linkId))
        .findFirst()
        .orElseThrow();
  }
}
