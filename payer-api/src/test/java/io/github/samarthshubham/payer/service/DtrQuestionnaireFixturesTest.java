package io.github.samarthshubham.payer.service;

import static io.github.samarthshubham.payer.support.GoldenFhir.fixture;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;

/** The DTR fixtures in fixtures/davinci/dtr line up with coverage-rules.yaml. */
class DtrQuestionnaireFixturesTest {

  private static final String QUESTIONNAIRE_BASE =
      "http://praxishealthplan.example/fhir/Questionnaire/";

  @SuppressWarnings("unchecked")
  static Stream<Map<String, Object>> rulesWithAQuestionnaire() throws IOException {
    try (InputStream input =
        DtrQuestionnaireFixturesTest.class.getResourceAsStream("/coverage-rules.yaml")) {
      Map<String, List<Map<String, Object>>> file = new Yaml().load(input);
      List<Map<String, Object>> rules =
          file.get("rules").stream().filter(rule -> rule.get("questionnaire") != null).toList();
      assertThat(rules).hasSize(4);
      return rules.stream();
    }
  }

  @ParameterizedTest
  @MethodSource("rulesWithAQuestionnaire")
  @SuppressWarnings("unchecked")
  void everyRuleQuestionnaireHasAFixtureWithTheCriteriaItems(Map<String, Object> rule) {
    String canonical = (String) rule.get("questionnaire");
    Questionnaire questionnaire = questionnaire(canonical);

    assertThat(questionnaire.getUrl()).isEqualTo(canonical);
    assertThat(questionnaire.getStatus()).isEqualTo(PublicationStatus.ACTIVE);
    List<Map<String, Object>> criteria =
        (List<Map<String, Object>>) rule.getOrDefault("autoApproveCriteria", List.of());
    for (Map<String, Object> criterion : criteria) {
      QuestionnaireItemComponent item =
          item(questionnaire.getItem(), (String) criterion.get("linkId"));
      assertThat(item).as("item %s in %s", criterion.get("linkId"), canonical).isNotNull();
      assertThat(item.getRequired()).isTrue();
      Set<QuestionnaireItemType> types =
          "EQUALS".equals(criterion.get("operator"))
              ? Set.of(QuestionnaireItemType.BOOLEAN)
              : Set.of(QuestionnaireItemType.INTEGER, QuestionnaireItemType.DECIMAL);
      assertThat(types).contains(item.getType());
    }
  }

  @ParameterizedTest
  @MethodSource("rulesWithAQuestionnaire")
  void everyLibraryAndValueSetAQuestionnaireNamesHasAFixture(Map<String, Object> rule) {
    Questionnaire questionnaire = questionnaire((String) rule.get("questionnaire"));
    String name = questionnaire.getIdPart();

    List<String> libraries =
        questionnaire.getExtensionsByUrl(QuestionnairePackageService.CQF_LIBRARY).stream()
            .map(extension -> ((CanonicalType) extension.getValue()).getValue())
            .toList();
    assertThat(libraries)
        .containsExactly(fixture(Library.class, "dtr/library-" + name + ".json").getUrl());
    List<String> valueSets = answerValueSets(questionnaire.getItem()).toList();
    assertThat(valueSets).isNotEmpty();
    for (String url : valueSets) {
      String id = url.substring(url.lastIndexOf('/') + 1);
      assertThat(fixture(ValueSet.class, "dtr/valueset-" + id + ".json").getUrl()).isEqualTo(url);
    }
  }

  private static Questionnaire questionnaire(String canonical) {
    String name = canonical.substring(QUESTIONNAIRE_BASE.length());
    return fixture(Questionnaire.class, "dtr/questionnaire-" + name + ".json");
  }

  private static QuestionnaireItemComponent item(
      List<QuestionnaireItemComponent> items, String linkId) {
    for (QuestionnaireItemComponent item : items) {
      if (linkId.equals(item.getLinkId())) {
        return item;
      }
      QuestionnaireItemComponent nested = item(item.getItem(), linkId);
      if (nested != null) {
        return nested;
      }
    }
    return null;
  }

  private static Stream<String> answerValueSets(List<QuestionnaireItemComponent> items) {
    return items.stream()
        .flatMap(
            item ->
                Stream.concat(
                    item.hasAnswerValueSet() ? Stream.of(item.getAnswerValueSet()) : Stream.empty(),
                    answerValueSets(item.getItem())));
  }
}
