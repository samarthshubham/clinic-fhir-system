package io.github.samarthshubham.clinic.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.samarthshubham.clinic.client.DtrPackage;
import io.github.samarthshubham.clinic.dto.DocumentationAnswer;
import io.github.samarthshubham.clinic.dto.DocumentationCoding;
import io.github.samarthshubham.clinic.dto.DocumentationEnableWhen;
import io.github.samarthshubham.clinic.dto.DocumentationItem;
import io.github.samarthshubham.clinic.dto.DocumentationResponse;
import io.github.samarthshubham.clinic.dto.DocumentationValue;
import io.github.samarthshubham.clinic.exception.DocumentationValidationException;
import io.github.samarthshubham.clinic.service.dtr.DtrFixtures;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseStatus;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DocumentationMapperTest {

  private static final String DTR_ANSWERS =
      "http://praxishealthplan.example/fhir/CodeSystem/dtr-answers";
  private static final String SNOMED = "http://snomed.info/sct";

  private final DocumentationMapper mapper = new DocumentationMapper();
  private final DtrPackage mri = DtrFixtures.mriPackage();

  @Test
  void theMriFormHasItsItemsOptionsEnableWhenAndPrefilledAnswers() {
    DocumentationResponse form = mapper.toResponse("sr1", mri, DtrFixtures.mriPrefilled());

    assertThat(form.title()).isNotBlank();
    assertThat(form.items()).extracting(DocumentationItem::type).first().isEqualTo("display");
    DocumentationItem diagnosis = item(form, "primary-diagnosis");
    assertThat(diagnosis.type()).isEqualTo("open-choice");
    assertThat(diagnosis.required()).isTrue();
    assertThat(diagnosis.options()).hasSize(6);
    assertThat(diagnosis.options().getFirst().valueCoding())
        .isEqualTo(new DocumentationCoding(SNOMED, "279039007", "Low back pain"));
    assertThat(diagnosis.answers())
        .containsExactly(new DocumentationAnswer(text("Lumbar radiculopathy"), true));

    DocumentationItem therapies = item(form, "conservative-therapy-types");
    assertThat(therapies.repeats()).isTrue();
    assertThat(therapies.options()).isNotEmpty();
    assertThat(therapies.answers()).hasSize(2).allMatch(DocumentationAnswer::prefilled);

    DocumentationItem description = item(form, "neurological-deficit-description");
    assertThat(description.enableWhen())
        .containsExactly(
            new DocumentationEnableWhen(
                "neurological-deficit",
                "=",
                new DocumentationValue(null, null, null, true, null, null, null)));
    assertThat(description.answers()).isEmpty();
    assertThat(item(form, "pain-location").options()).isEmpty();
  }

  @Test
  void manualAnswersAreNotPrefilled() {
    QuestionnaireResponse response = DtrFixtures.mriPrefilled();
    response.getItem().get(0).getItem().get(0).getAnswerFirstRep().getExtension().clear();
    response
        .getItem()
        .get(0)
        .getItem()
        .get(0)
        .getAnswerFirstRep()
        .addExtension(DocumentationMapper.origin(DocumentationMapper.MANUAL, "pr1"));

    DocumentationResponse form = mapper.toResponse("sr1", mri, response);

    assertThat(item(form, "primary-diagnosis").answers().getFirst().prefilled()).isFalse();
  }

  @Test
  void aValueSetWithoutExpansionGivesItsComposedConcepts() {
    mri.valueSets().getFirst().getExpansion().getContains().clear();

    DocumentationResponse form = mapper.toResponse("sr1", mri, DtrFixtures.mriPrefilled());

    assertThat(item(form, "primary-diagnosis").options()).hasSize(6);
  }

  @Test
  void anUnknownValueSetGivesNoOptions() {
    DtrPackage withoutValueSets = new DtrPackage(mri.questionnaire(), null, List.of());

    DocumentationResponse form =
        mapper.toResponse("sr1", withoutValueSets, DtrFixtures.mriPrefilled());

    assertThat(item(form, "primary-diagnosis").options()).isEmpty();
  }

  @Test
  void toFhirStartsADtrResponseLinkedToTheOrderAndCoverage() {
    QuestionnaireResponse response =
        mapper.toFhir(
            "sr1",
            "p1",
            "cov1",
            "pr1",
            new DtrPackage(mri.questionnaire().setVersion("1.0.0"), null, List.of()),
            QuestionnaireResponseStatus.INPROGRESS,
            Date.from(Instant.parse("2026-11-19T10:00:00Z")));

    assertThat(response.getMeta().getProfile().getFirst().getValue())
        .isEqualTo(DocumentationMapper.QUESTIONNAIRE_RESPONSE_PROFILE);
    assertThat(response.getQuestionnaire()).isEqualTo(DtrFixtures.MRI_CANONICAL + "|1.0.0");
    assertThat(response.getAuthoredElement().getValueAsString())
        .isEqualTo("2026-11-19T10:00:00+00:00");
    assertThat(response.getAuthor().getReference()).isEqualTo("Practitioner/pr1");
    assertThat(response.getExtensionByUrl(DocumentationMapper.INTENDED_USE).getValue())
        .satisfies(
            use ->
                assertThat(((CodeableConcept) use).getCodingFirstRep().getCode())
                    .isEqualTo("withpa"));
  }

  @Test
  void toFhirWithoutAnAuthorLeavesItOut() {
    QuestionnaireResponse response =
        mapper.toFhir(
            "sr1", "p1", "cov1", null, mri, QuestionnaireResponseStatus.COMPLETED, new Date());

    assertThat(response.hasAuthor()).isFalse();
  }

  @Test
  void answerValuesFollowTheQuestionType() {
    assertThat(value(QuestionnaireItemType.BOOLEAN, bool(true))).isInstanceOf(BooleanType.class);
    assertThat(value(QuestionnaireItemType.INTEGER, integer(3))).isInstanceOf(IntegerType.class);
    assertThat(value(QuestionnaireItemType.DECIMAL, integer(3)))
        .isInstanceOfSatisfying(
            DecimalType.class, d -> assertThat(d.getValue()).isEqualByComparingTo("3"));
    assertThat(
            value(
                QuestionnaireItemType.DECIMAL,
                new DocumentationValue(null, null, new BigDecimal("2.5"), null, null, null, null)))
        .isInstanceOf(DecimalType.class);
    assertThat(value(QuestionnaireItemType.DATE, date("2026-10"))).isInstanceOf(DateType.class);
    assertThat(
            value(
                QuestionnaireItemType.DATETIME,
                new DocumentationValue(null, null, null, null, null, "2026-10-05T09:30:00Z", null)))
        .isInstanceOf(DateTimeType.class);
    assertThat(value(QuestionnaireItemType.TEXT, text("Notes"))).isInstanceOf(StringType.class);
    assertThat(value(QuestionnaireItemType.OPENCHOICE, text("Other")))
        .isInstanceOf(StringType.class);
  }

  @Test
  void aChoiceAnswerMustBeAnOptionAndGetsItsDisplay() {
    QuestionnaireItemComponent therapies =
        question(mri.questionnaire(), "conservative-therapy-types");

    Type answer = mapper.toAnswerValue(therapies, coding(DTR_ANSWERS, "physical-therapy"), mri);

    assertThat(answer)
        .isInstanceOfSatisfying(
            Coding.class, c -> assertThat(c.getDisplay()).isEqualTo("Physical therapy"));
    assertThatThrownBy(() -> mapper.toAnswerValue(therapies, coding(DTR_ANSWERS, "surgery"), mri))
        .isInstanceOf(DocumentationValidationException.class)
        .hasMessage("'surgery' is not an answer option of 'conservative-therapy-types'");
    assertThatThrownBy(() -> mapper.toAnswerValue(therapies, text("Physical therapy"), mri))
        .isInstanceOf(DocumentationValidationException.class)
        .hasMessage("The answer to 'conservative-therapy-types' must be a choice value");
  }

  @Test
  void anOpenChoiceCodingMustBeFromItsValueSet() {
    QuestionnaireItemComponent diagnosis = question(mri.questionnaire(), "primary-diagnosis");

    assertThat(mapper.toAnswerValue(diagnosis, coding(SNOMED, "23056005"), mri))
        .isInstanceOfSatisfying(
            Coding.class, c -> assertThat(c.getDisplay()).isEqualTo("Sciatica"));
    assertThatThrownBy(() -> mapper.toAnswerValue(diagnosis, coding(SNOMED, "1"), mri))
        .isInstanceOf(DocumentationValidationException.class);
  }

  @Test
  void anAnswerNeedsExactlyOneValue() {
    QuestionnaireItemComponent item = item("q", QuestionnaireItemType.STRING);

    assertThatThrownBy(
            () ->
                mapper.toAnswerValue(
                    item, new DocumentationValue("a", 1, null, null, null, null, null), mri))
        .isInstanceOf(DocumentationValidationException.class)
        .hasMessage("Each answer to 'q' needs exactly one value");
    assertThatThrownBy(
            () ->
                mapper.toAnswerValue(
                    item, new DocumentationValue(null, null, null, null, null, null, null), mri))
        .isInstanceOf(DocumentationValidationException.class);
  }

  @Test
  void invalidValuesAreValidationErrors() {
    assertThatThrownBy(() -> value(QuestionnaireItemType.DATE, date("05/10/2026")))
        .isInstanceOf(DocumentationValidationException.class)
        .hasMessage("The answer to 'q' is not a valid date value");
    assertThatThrownBy(() -> value(QuestionnaireItemType.STRING, text("  ")))
        .isInstanceOf(DocumentationValidationException.class);
    assertThatThrownBy(() -> value(QuestionnaireItemType.INTEGER, text("3")))
        .isInstanceOf(DocumentationValidationException.class)
        .hasMessage("The answer to 'q' must be a integer value");
  }

  @ParameterizedTest
  @EnumSource(
      value = QuestionnaireItemType.class,
      names = {"GROUP", "DISPLAY", "ATTACHMENT"})
  void itemsWithoutSupportedAnswersTakeNone(QuestionnaireItemType type) {
    assertThatThrownBy(() -> value(type, text("x")))
        .isInstanceOf(DocumentationValidationException.class)
        .hasMessageContaining("takes no answers");
  }

  @Test
  void toValueCoversTheAnswerTypes() {
    assertThat(
            DocumentationMapper.toValue(new DateTimeType("2026-10-05T09:30:00Z")).valueDateTime())
        .isEqualTo("2026-10-05T09:30:00Z");
    assertThat(DocumentationMapper.toValue(new DecimalType("2.5")).valueDecimal())
        .isEqualByComparingTo("2.5");
    assertThat(DocumentationMapper.toValue(new Quantity(3)))
        .isEqualTo(new DocumentationValue(null, null, null, null, null, null, null));
    assertThat(DocumentationMapper.toValue(null))
        .isEqualTo(new DocumentationValue(null, null, null, null, null, null, null));
  }

  @Test
  void originIsNullWithoutTheExtension() {
    assertThat(
            DocumentationMapper.origin(
                new QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent()))
        .isNull();
  }

  private Type value(QuestionnaireItemType type, DocumentationValue value) {
    return mapper.toAnswerValue(item("q", type), value, mri);
  }

  private static QuestionnaireItemComponent item(String linkId, QuestionnaireItemType type) {
    return new QuestionnaireItemComponent().setLinkId(linkId).setType(type);
  }

  private static QuestionnaireItemComponent question(Questionnaire questionnaire, String linkId) {
    List<QuestionnaireItemComponent> all = new ArrayList<>();
    flatten(questionnaire.getItem(), all);
    return all.stream().filter(i -> i.getLinkId().equals(linkId)).findFirst().orElseThrow();
  }

  private static void flatten(
      List<QuestionnaireItemComponent> items, List<QuestionnaireItemComponent> all) {
    for (QuestionnaireItemComponent item : items) {
      all.add(item);
      flatten(item.getItem(), all);
    }
  }

  private static DocumentationItem item(DocumentationResponse form, String linkId) {
    List<DocumentationItem> all = new ArrayList<>();
    flattenItems(form.items(), all);
    return all.stream().filter(i -> i.linkId().equals(linkId)).findFirst().orElseThrow();
  }

  private static void flattenItems(List<DocumentationItem> items, List<DocumentationItem> all) {
    for (DocumentationItem item : items) {
      all.add(item);
      flattenItems(item.items(), all);
    }
  }

  private static DocumentationValue text(String value) {
    return new DocumentationValue(value, null, null, null, null, null, null);
  }

  private static DocumentationValue integer(int value) {
    return new DocumentationValue(null, value, null, null, null, null, null);
  }

  private static DocumentationValue bool(boolean value) {
    return new DocumentationValue(null, null, null, value, null, null, null);
  }

  private static DocumentationValue date(String value) {
    return new DocumentationValue(null, null, null, null, value, null, null);
  }

  private static DocumentationValue coding(String system, String code) {
    return new DocumentationValue(
        null, null, null, null, null, null, new DocumentationCoding(system, code, null));
  }
}
