package io.github.samarthshubham.clinic.service.dtr;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static io.github.samarthshubham.clinic.support.GoldenFhir.assertMatchesGolden;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.client.DtrClient;
import io.github.samarthshubham.clinic.client.DtrPackage;
import io.github.samarthshubham.clinic.client.PayerApiException;
import io.github.samarthshubham.clinic.dto.DocumentationAnswer;
import io.github.samarthshubham.clinic.dto.DocumentationCoding;
import io.github.samarthshubham.clinic.dto.DocumentationItem;
import io.github.samarthshubham.clinic.dto.DocumentationItemAnswers;
import io.github.samarthshubham.clinic.dto.DocumentationRequest;
import io.github.samarthshubham.clinic.dto.DocumentationResponse;
import io.github.samarthshubham.clinic.dto.DocumentationValue;
import io.github.samarthshubham.clinic.exception.DocumentationConflictException;
import io.github.samarthshubham.clinic.exception.DocumentationNotFoundException;
import io.github.samarthshubham.clinic.exception.DocumentationValidationException;
import io.github.samarthshubham.clinic.mapper.DocumentationMapper;
import io.github.samarthshubham.clinic.service.CrdService;
import io.github.samarthshubham.clinic.service.ServiceRequestService;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemOperator;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseStatus;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DtrServiceTest {

  private static final String ORDER_ID = "mri-sandra-okafor";
  private static final String PATIENT_ID = "seed-sandra-okafor";
  private static final String COVERAGE_ID = "coverage-sandra-okafor";
  private static final String CLINICIAN = "pr-anika-patel";
  private static final String REQUESTER = "pr-requester";
  private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

  private final FhirClientStub fhir = new FhirClientStub();
  private final ServiceRequestService serviceRequestService = mock(ServiceRequestService.class);
  private final CrdService crdService = mock(CrdService.class);
  private final DtrClient dtrClient = mock(DtrClient.class);
  private final DtrPrepopulationService prepopulation = mock(DtrPrepopulationService.class);
  private final DtrService service =
      new DtrService(
          fhir.client(),
          serviceRequestService,
          crdService,
          dtrClient,
          prepopulation,
          new DocumentationMapper(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  private DtrPackage questionnairePackage = DtrFixtures.mriPackage();

  @BeforeEach
  void givenSandrasMriOrder() {
    ServiceRequest order = new ServiceRequest();
    order.setId("http://localhost:8080/fhir/ServiceRequest/" + ORDER_ID + "/_history/1");
    order.setSubject(new Reference("Patient/" + PATIENT_ID));
    order.setRequester(new Reference("Practitioner/" + REQUESTER));
    when(serviceRequestService.findServiceRequest(ORDER_ID)).thenReturn(order);
    Coverage coverage = new Coverage();
    coverage.setId("http://localhost:8080/fhir/Coverage/" + COVERAGE_ID + "/_history/3");
    when(crdService.activeCoverageWithPayer(PATIENT_ID)).thenReturn(coverage);
    when(dtrClient.questionnairePackage(any(), any()))
        .thenAnswer(invocation -> Optional.of(questionnairePackage));
    when(prepopulation.prepopulate(any(), any())).thenAnswer(i -> DtrFixtures.mriPrefilled());
  }

  @Test
  void getReturnsTheChartPrefilledFormWithoutSavingIt() {
    DocumentationResponse form = service.getDocumentation(ORDER_ID);

    assertThat(form.serviceRequestId()).isEqualTo(ORDER_ID);
    assertThat(form.questionnaireResponseId()).isNull();
    assertThat(form.questionnaire()).isEqualTo(DtrFixtures.MRI_CANONICAL);
    assertThat(form.status()).isEqualTo("in-progress");
    List<DocumentationItem> questions = questions(form.items());
    assertThat(questions).hasSize(10);
    assertThat(questions.stream().filter(q -> !q.answers().isEmpty())).hasSize(7);
    assertThat(questions.stream().flatMap(q -> q.answers().stream()))
        .allMatch(DocumentationAnswer::prefilled);
    assertThat(fhir.created()).isEmpty();
    assertThat(fhir.lastSearchCriteria(QuestionnaireResponse.class))
        .contains("subject=Patient/" + PATIENT_ID);
  }

  @Test
  void getReturnsTheSavedResponseForTheOrder() {
    QuestionnaireResponse other = savedResponse("qr-other", "other-order");
    other.setStatus(QuestionnaireResponseStatus.COMPLETED);
    fhir.givenSearchResult(
        QuestionnaireResponse.class,
        bundleOf(other, savedResponse("qr1", ORDER_ID), savedResponse("qr0", ORDER_ID)));

    DocumentationResponse form = service.getDocumentation(ORDER_ID);

    assertThat(form.questionnaireResponseId()).isEqualTo("qr1");
    assertThat(form.status()).isEqualTo("in-progress");
    verify(prepopulation, never()).prepopulate(any(), any());
  }

  @Test
  void completeKeepsPrefilledOriginsAndRecordsOverridesAndManualAnswersWithTheirAuthor() {
    DocumentationResponse form =
        service.completeDocumentation(
            ORDER_ID,
            request(
                answers("primary-diagnosis", text("Lumbar radiculopathy")),
                answers("conservative-therapy-weeks", integer(7)),
                answers("pain-location", text("L5, left side"))),
            CLINICIAN);

    QuestionnaireResponse saved = createdResponse();
    assertMatchesGolden(saved, "dtr/qr-mri-completed.json");
    assertThat(saved.getStatus()).isEqualTo(QuestionnaireResponseStatus.COMPLETED);
    assertThat(saved.getExtensionByUrl(DocumentationMapper.QR_CONTEXT).getValue())
        .isInstanceOfSatisfying(
            Reference.class,
            ref -> assertThat(ref.getReference()).isEqualTo("ServiceRequest/" + ORDER_ID));
    assertThat(saved.hasBasedOn()).isFalse();
    assertThat(origin(saved, "primary-diagnosis")).isEqualTo("auto-client");
    assertThat(origin(saved, "conservative-therapy-weeks")).isEqualTo("override");
    assertThat(origin(saved, "pain-location")).isEqualTo("manual");
    assertThat(author(saved, "pain-location")).isEqualTo("Practitioner/" + CLINICIAN);
    assertThat(form.questionnaireResponseId()).isEqualTo("1");
    assertThat(form.status()).isEqualTo("completed");
    assertThat(item(form.items(), "conservative-therapy-weeks").answers())
        .containsExactly(new DocumentationAnswer(integer(7), false));
  }

  @Test
  void saveCreatesAnInProgressResponseAndLaterSavesUpdateIt() {
    service.saveDocumentation(
        ORDER_ID, request(answers("pain-location", text("L5, left side"))), CLINICIAN);

    QuestionnaireResponse first = createdResponse();
    assertThat(first.getStatus()).isEqualTo(QuestionnaireResponseStatus.INPROGRESS);
    assertThat(first.getAuthor().getReference()).isEqualTo("Practitioner/" + CLINICIAN);
    assertThat(answered(first)).contains("pain-location", "primary-diagnosis");

    fhir.givenSearchResult(
        QuestionnaireResponse.class, bundleOf(fhir.stored(QuestionnaireResponse.class, "1")));
    DocumentationResponse second =
        service.saveDocumentation(
            ORDER_ID, request(answers("pain-location", text("L4, both sides"))), CLINICIAN);

    assertThat(fhir.created()).hasSize(1);
    assertThat(fhir.updated()).hasSize(1);
    assertThat(second.questionnaireResponseId()).isEqualTo("1");
    QuestionnaireResponse updated = fhir.stored(QuestionnaireResponse.class, "1");
    assertThat(answer(updated, "pain-location").getValue().primitiveValue())
        .isEqualTo("L4, both sides");
    assertThat(origin(updated, "pain-location")).isEqualTo("manual");
    verify(prepopulation).prepopulate(any(), any());
  }

  @Test
  void anEmptyValueListClearsAnAnswer() {
    service.saveDocumentation(ORDER_ID, request(answers("prior-imaging-date")), CLINICIAN);

    assertThat(answered(createdResponse())).doesNotContain("prior-imaging-date");
  }

  @Test
  void answersOfDisabledItemsAreDropped() {
    service.completeDocumentation(
        ORDER_ID, request(answers("prior-imaging-done", bool(false))), CLINICIAN);

    QuestionnaireResponse saved = createdResponse();
    assertThat(answered(saved)).contains("prior-imaging-done").doesNotContain("prior-imaging-date");
    assertThat(origin(saved, "prior-imaging-done")).isEqualTo("override");
  }

  @Test
  void completeNeedsEveryEnabledRequiredItem() {
    assertThatThrownBy(
            () ->
                service.completeDocumentation(
                    ORDER_ID,
                    request(
                        answers("symptom-onset-date"), answers("neurological-deficit", bool(true))),
                    CLINICIAN))
        .isInstanceOf(DocumentationValidationException.class)
        .hasMessage(
            "Required questions are not answered: symptom-onset-date,"
                + " neurological-deficit-description");
    assertThat(fhir.created()).isEmpty();
  }

  @Test
  void completeWithoutABodyCompletesTheSavedAnswers() {
    service.completeDocumentation(ORDER_ID, null, CLINICIAN);

    assertThat(createdResponse().getStatus()).isEqualTo(QuestionnaireResponseStatus.COMPLETED);
  }

  @Test
  void aCompletedResponseCannotBeChanged() {
    QuestionnaireResponse completed = savedResponse("qr1", ORDER_ID);
    completed.setStatus(QuestionnaireResponseStatus.COMPLETED);
    fhir.givenSearchResult(QuestionnaireResponse.class, bundleOf(completed));

    assertThatThrownBy(() -> service.saveDocumentation(ORDER_ID, request(), CLINICIAN))
        .isInstanceOf(DocumentationConflictException.class)
        .hasMessageContaining("already completed");
    assertThat(fhir.updated()).isEmpty();
  }

  @Test
  void theOrderRequesterAuthorsAnswersWhenTheUserIsNoPractitioner() {
    service.saveDocumentation(ORDER_ID, request(answers("pain-location", text("L5"))), null);

    assertThat(author(createdResponse(), "pain-location")).isEqualTo("Practitioner/" + REQUESTER);
  }

  @Test
  void aChangedAnswerNeedsAPractitionerAuthor() {
    ServiceRequest order = serviceRequestService.findServiceRequest(ORDER_ID);
    order.setRequester(null);

    assertThatThrownBy(
            () ->
                service.saveDocumentation(
                    ORDER_ID, request(answers("pain-location", text("L5"))), null))
        .isInstanceOf(DocumentationValidationException.class)
        .hasMessageContaining("pain-location");
  }

  @ParameterizedTest
  @CsvSource({
    "unknown-question, The form has no question 'unknown-question'",
    "clinical-indication, The form has no question 'clinical-indication'",
  })
  void answersMustBeForQuestionsOfTheForm(String linkId, String message) {
    assertThatThrownBy(
            () ->
                service.saveDocumentation(ORDER_ID, request(answers(linkId, text("x"))), CLINICIAN))
        .isInstanceOf(DocumentationValidationException.class)
        .hasMessage(message);
  }

  @Test
  void aSingleAnswerQuestionTakesOneValue() {
    assertThatThrownBy(
            () ->
                service.saveDocumentation(
                    ORDER_ID, request(answers("pain-location", text("L4"), text("L5"))), CLINICIAN))
        .isInstanceOf(DocumentationValidationException.class)
        .hasMessage("Question 'pain-location' takes one answer");
  }

  @Test
  void withoutPayerCoverageThereIsNoDocumentation() {
    when(crdService.activeCoverageWithPayer(PATIENT_ID)).thenReturn(null);

    assertThatThrownBy(() -> service.getDocumentation(ORDER_ID))
        .isInstanceOf(DocumentationNotFoundException.class)
        .hasMessageContaining("no active coverage with the payer");
    verify(dtrClient, never()).questionnairePackage(any(), any());
  }

  @Test
  void anOrderThePayerNeedsNoDocumentationForHasNone() {
    when(dtrClient.questionnairePackage(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDocumentation(ORDER_ID))
        .isInstanceOf(DocumentationNotFoundException.class)
        .hasMessage("The payer needs no documentation for ServiceRequest '" + ORDER_ID + "'");
  }

  @Test
  void aPayerFailureIsPassedOn() {
    when(dtrClient.questionnairePackage(any(), any()))
        .thenThrow(new PayerApiException("payer-api could not be reached"));

    assertThatThrownBy(() -> service.getDocumentation(ORDER_ID))
        .isInstanceOf(PayerApiException.class);
  }

  @Test
  void enableWhenSupportsAnyBehaviourExistsAndComparisons() {
    questionnairePackage = packageOf(enableWhenQuestionnaire());
    when(prepopulation.prepopulate(any(), any()))
        .thenAnswer(
            i -> new QuestionnaireResponse().setStatus(QuestionnaireResponseStatus.INPROGRESS));

    service.saveDocumentation(
        ORDER_ID,
        request(
            answers("score", integer(5)),
            answers("weight", decimal("80.5")),
            answers("date", date("2026-01-10")),
            answers("any", text("a")),
            answers("exists", text("b")),
            answers("missing-exists", text("c")),
            answers("gt", text("d")),
            answers("lt", text("e")),
            answers("ge", text("f")),
            answers("le", text("g")),
            answers("ne", text("h")),
            answers("date-after", text("i")),
            answers("all-unmet", text("j"))),
        CLINICIAN);

    assertThat(answered(createdResponse()))
        .containsExactlyInAnyOrder(
            "score", "weight", "date", "any", "exists", "gt", "ge", "le", "ne", "date-after");
  }

  @Test
  void codingsMatchBySystemAndCode() {
    Coding coding = new Coding("http://example.org", "a", "A");

    assertThat(DtrService.sameValue(coding, new Coding("http://example.org", "a", null))).isTrue();
    assertThat(DtrService.sameValue(coding, new Coding("http://example.org", "b", "A"))).isFalse();
    assertThat(DtrService.sameValue(new IntegerType(5), new DecimalType("5.0"))).isTrue();
    assertThat(DtrService.sameValue(new StringType("x"), new BooleanType(true))).isFalse();
    assertThat(DtrService.sameValue(new DateType("2026-01-01"), new StringType("2026-01-01")))
        .isTrue();
    assertThat(DtrService.sameValue(new CodeableConcept(), new CodeableConcept())).isFalse();
  }

  private static Questionnaire enableWhenQuestionnaire() {
    Questionnaire questionnaire = new Questionnaire();
    questionnaire.setUrl("http://example.org/Questionnaire/enable-when");
    questionnaire.addItem(question("score", QuestionnaireItemType.INTEGER));
    questionnaire.addItem(question("weight", QuestionnaireItemType.DECIMAL));
    questionnaire.addItem(question("date", QuestionnaireItemType.DATE));
    QuestionnaireItemComponent any = question("any", QuestionnaireItemType.STRING);
    any.setEnableBehavior(Questionnaire.EnableWhenBehavior.ANY);
    condition(any, "score", QuestionnaireItemOperator.EQUAL, new IntegerType(1));
    condition(any, "score", QuestionnaireItemOperator.EQUAL, new IntegerType(5));
    questionnaire.addItem(any);
    questionnaire.addItem(
        condition(
            question("exists", QuestionnaireItemType.STRING),
            "weight",
            QuestionnaireItemOperator.EXISTS,
            new BooleanType(true)));
    questionnaire.addItem(
        condition(
            question("missing-exists", QuestionnaireItemType.STRING),
            "weight",
            QuestionnaireItemOperator.EXISTS,
            new BooleanType(false)));
    questionnaire.addItem(
        condition(
            question("gt", QuestionnaireItemType.STRING),
            "weight",
            QuestionnaireItemOperator.GREATER_THAN,
            new DecimalType("80")));
    questionnaire.addItem(
        condition(
            question("lt", QuestionnaireItemType.STRING),
            "score",
            QuestionnaireItemOperator.LESS_THAN,
            new IntegerType(5)));
    questionnaire.addItem(
        condition(
            question("ge", QuestionnaireItemType.STRING),
            "score",
            QuestionnaireItemOperator.GREATER_OR_EQUAL,
            new IntegerType(5)));
    questionnaire.addItem(
        condition(
            question("le", QuestionnaireItemType.STRING),
            "score",
            QuestionnaireItemOperator.LESS_OR_EQUAL,
            new IntegerType(5)));
    questionnaire.addItem(
        condition(
            question("ne", QuestionnaireItemType.STRING),
            "score",
            QuestionnaireItemOperator.NOT_EQUAL,
            new IntegerType(4)));
    questionnaire.addItem(
        condition(
            question("date-after", QuestionnaireItemType.STRING),
            "date",
            QuestionnaireItemOperator.GREATER_THAN,
            new DateType("2026-01-01")));
    QuestionnaireItemComponent allUnmet = question("all-unmet", QuestionnaireItemType.STRING);
    condition(allUnmet, "score", QuestionnaireItemOperator.EQUAL, new IntegerType(5));
    condition(allUnmet, "weight", QuestionnaireItemOperator.LESS_THAN, new DecimalType("1"));
    questionnaire.addItem(allUnmet);
    return questionnaire;
  }

  private static QuestionnaireItemComponent question(String linkId, QuestionnaireItemType type) {
    return new QuestionnaireItemComponent().setLinkId(linkId).setText(linkId).setType(type);
  }

  private static QuestionnaireItemComponent condition(
      QuestionnaireItemComponent item,
      String question,
      QuestionnaireItemOperator operator,
      Type answer) {
    item.addEnableWhen().setQuestion(question).setOperator(operator).setAnswer(answer);
    return item;
  }

  private static DtrPackage packageOf(Questionnaire questionnaire) {
    return new DtrPackage(questionnaire, null, List.of());
  }

  private QuestionnaireResponse savedResponse(String id, String orderId) {
    QuestionnaireResponse response = DtrFixtures.mriPrefilled();
    response.setId(id);
    response.addExtension(
        DocumentationMapper.QR_CONTEXT, new Reference("ServiceRequest/" + orderId));
    return response;
  }

  private QuestionnaireResponse createdResponse() {
    assertThat(fhir.created()).hasSize(1);
    return fhir.stored(QuestionnaireResponse.class, "1");
  }

  private static DocumentationRequest request(DocumentationItemAnswers... answers) {
    return new DocumentationRequest(List.of(answers));
  }

  private static DocumentationItemAnswers answers(String linkId, DocumentationValue... values) {
    return new DocumentationItemAnswers(linkId, List.of(values));
  }

  static DocumentationValue text(String value) {
    return new DocumentationValue(value, null, null, null, null, null, null);
  }

  static DocumentationValue integer(int value) {
    return new DocumentationValue(null, value, null, null, null, null, null);
  }

  static DocumentationValue decimal(String value) {
    return new DocumentationValue(null, null, new BigDecimal(value), null, null, null, null);
  }

  static DocumentationValue bool(boolean value) {
    return new DocumentationValue(null, null, null, value, null, null, null);
  }

  static DocumentationValue date(String value) {
    return new DocumentationValue(null, null, null, null, value, null, null);
  }

  static DocumentationValue coding(String system, String code) {
    return new DocumentationValue(
        null, null, null, null, null, null, new DocumentationCoding(system, code, null));
  }

  private static List<DocumentationItem> questions(List<DocumentationItem> items) {
    List<DocumentationItem> questions = new ArrayList<>();
    for (DocumentationItem item : items) {
      if (!List.of("group", "display").contains(item.type())) {
        questions.add(item);
      }
      questions.addAll(questions(item.items()));
    }
    return questions;
  }

  private static DocumentationItem item(List<DocumentationItem> items, String linkId) {
    return questions(items).stream()
        .filter(item -> item.linkId().equals(linkId))
        .findFirst()
        .orElseThrow();
  }

  private static List<String> answered(QuestionnaireResponse response) {
    return flatten(response.getItem())
        .filter(QuestionnaireResponseItemComponent::hasAnswer)
        .map(QuestionnaireResponseItemComponent::getLinkId)
        .toList();
  }

  private static Stream<QuestionnaireResponseItemComponent> flatten(
      List<QuestionnaireResponseItemComponent> items) {
    return items.stream().flatMap(item -> Stream.concat(Stream.of(item), flatten(item.getItem())));
  }

  private static QuestionnaireResponseItemAnswerComponent answer(
      QuestionnaireResponse response, String linkId) {
    return flatten(response.getItem())
        .filter(item -> item.getLinkId().equals(linkId))
        .findFirst()
        .orElseThrow()
        .getAnswerFirstRep();
  }

  private static String origin(QuestionnaireResponse response, String linkId) {
    return DocumentationMapper.origin(answer(response, linkId));
  }

  private static String author(QuestionnaireResponse response, String linkId) {
    Extension author =
        answer(response, linkId)
            .getExtensionByUrl(DocumentationMapper.INFORMATION_ORIGIN)
            .getExtensionByUrl("author");
    return ((Reference) author.getExtensionByUrl("practitioner").getValue()).getReference();
  }
}
