package io.github.samarthshubham.clinic.dtr;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static io.github.samarthshubham.clinic.support.GoldenFhir.assertMatchesGolden;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.service.PatientService;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Expression;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class DtrPrepopulationServiceTest {

  private static final String PATIENT_ID = "seed-sandra-okafor";
  private static final String CONDITIONS =
      "Condition?patient=seed-sandra-okafor&clinical-status=active&_count=100";
  private static final String MEDICATIONS =
      "MedicationStatement?patient=seed-sandra-okafor&_sort=effective&_count=100";
  private static final String ENCOUNTERS =
      "Encounter?patient=seed-sandra-okafor&_sort=date&_count=100";
  private static final String REPORTS =
      "DiagnosticReport?patient=seed-sandra-okafor&_sort=-issued&_count=100";

  private final FhirClientStub fhir = new FhirClientStub();
  private final PatientService patientService = mock(PatientService.class);
  private final DtrPrepopulationService service =
      new DtrPrepopulationService(
          patientService, new DtrExpressionEvaluator(FhirContext.forR4Cached(), fhir.client()));

  @Test
  void mriLumbarSpineIsPrefilledFromTheChartAndMatchesTheGoldenResponse() {
    givenSandrasChart();

    QuestionnaireResponse response = service.prepopulate(mriQuestionnaire(), PATIENT_ID);

    assertMatchesGolden(response, "dtr/qr-mri-prefilled.json");
    List<QuestionnaireResponseItemComponent> questions = questions(response.getItem());
    long prefilled =
        questions.stream().filter(QuestionnaireResponseItemComponent::hasAnswer).count();
    assertThat(prefilled * 100 / questions.size()).isGreaterThanOrEqualTo(60);
    assertThat(questions.stream().flatMap(q -> q.getAnswer().stream()))
        .allSatisfy(
            answer ->
                assertThat(
                        answer
                            .getExtensionByUrl(DtrPrepopulationService.INFORMATION_ORIGIN)
                            .getExtensionByUrl("source")
                            .getValue())
                    .isInstanceOfSatisfying(
                        CodeType.class,
                        code -> assertThat(code.getCode()).isEqualTo("auto-client")));
  }

  @Test
  void aFailedQueryLeavesTheItemsThatNeedItEmptyAndLogsNoPhi(CapturedOutput output) {
    givenSandrasChart();
    fhir.givenSearchByUrlFails(REPORTS, new InternalErrorException("Sandra Okafor 1980-02-19"));

    QuestionnaireResponse response = service.prepopulate(mriQuestionnaire(), PATIENT_ID);

    assertThat(answered(response))
        .contains("primary-diagnosis", "conservative-therapy-weeks")
        .doesNotContain("prior-imaging-done", "prior-imaging-date");
    assertThat(output)
        .contains("variable reports failed (questionnaire mri-lumbar-spine, linkId null)")
        .contains("InternalErrorException")
        .doesNotContain("Sandra")
        .doesNotContain("1980-02-19");
  }

  @Test
  void anExpressionErrorLeavesOnlyThatItemEmptyAndLogsTheLinkId(CapturedOutput output) {
    givenPatient();
    Questionnaire questionnaire =
        questionnaire(
            question("broken", QuestionnaireItemType.STRING, "%patient.name.given.foo("),
            question("family", QuestionnaireItemType.STRING, "%patient.name.family"));

    QuestionnaireResponse response = service.prepopulate(questionnaire, PATIENT_ID);

    assertThat(answered(response)).containsExactly("family");
    assertThat(output)
        .contains("initialExpression failed (questionnaire test, linkId broken)")
        .doesNotContain("Okafor");
  }

  @Test
  void aResultThatDoesNotFitTheItemTypeIsAnExpressionError(CapturedOutput output) {
    givenPatient();
    Questionnaire questionnaire =
        questionnaire(question("age", QuestionnaireItemType.INTEGER, "%patient.name.family"));

    QuestionnaireResponse response = service.prepopulate(questionnaire, PATIENT_ID);

    assertThat(answered(response)).isEmpty();
    assertThat(output).contains("linkId age").contains("IllegalArgumentException");
  }

  @Test
  void itemVariablesAreScopedToTheirItemAndChildren() {
    givenPatient();
    QuestionnaireItemComponent group =
        new QuestionnaireItemComponent().setLinkId("group").setType(QuestionnaireItemType.GROUP);
    group.addExtension(
        DtrPrepopulationService.VARIABLE, fhirPath("family", "%patient.name.family"));
    group.addItem(question("inside", QuestionnaireItemType.STRING, "%family"));
    Questionnaire questionnaire =
        questionnaire(group, question("outside", QuestionnaireItemType.STRING, "%family"));

    QuestionnaireResponse response = service.prepopulate(questionnaire, PATIENT_ID);

    assertThat(answered(response)).containsExactly("inside");
  }

  @Test
  void withoutAPatientLaunchContextThereIsNoPatientVariable() {
    givenPatient();
    Questionnaire questionnaire =
        questionnaire(question("family", QuestionnaireItemType.STRING, "%patient.name.family"));
    questionnaire.getExtension().clear();

    QuestionnaireResponse response = service.prepopulate(questionnaire, PATIENT_ID);

    assertThat(answered(response)).isEmpty();
    assertThat(response.getSubject().getReference()).isEqualTo("Patient/" + PATIENT_ID);
  }

  @Test
  void unknownPatientIsNotFound() {
    when(patientService.findPatient("nobody"))
        .thenThrow(new ResourceNotFoundException("Patient", "nobody"));

    assertThatThrownBy(() -> service.prepopulate(mriQuestionnaire(), "nobody"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @ParameterizedTest
  @CsvSource({
    "2024, 2024",
    "2024-05, 2024-05",
    "2026-10-05, 2026-10-05",
    "2026-10-05T23:30:00-05:00, 2026-10-05"
  })
  void dateAnswersKeepTheSourcePrecisionUpToADay(String source, String expected) {
    assertThat(
            DtrPrepopulationService.answerValue(
                QuestionnaireItemType.DATE, new DateTimeType(source)))
        .isInstanceOfSatisfying(
            DateType.class, date -> assertThat(date.getValueAsString()).isEqualTo(expected));
  }

  @Test
  void answerValuesAreConvertedToTheItemType() {
    assertThat(
            DtrPrepopulationService.answerValue(
                QuestionnaireItemType.DATE, new DateTimeType("2026-10-05T10:00:00+05:30")))
        .isInstanceOfSatisfying(
            DateType.class, date -> assertThat(date.getValueAsString()).isEqualTo("2026-10-05"));
    assertThat(
            DtrPrepopulationService.answerValue(
                QuestionnaireItemType.DATETIME, new DateTimeType("2026-10-05")))
        .isInstanceOf(DateTimeType.class);
    assertThat(
            DtrPrepopulationService.answerValue(QuestionnaireItemType.DECIMAL, new IntegerType(4)))
        .isInstanceOfSatisfying(
            DecimalType.class, d -> assertThat(d.getValueAsString()).isEqualTo("4"));
    assertThat(
            DtrPrepopulationService.answerValue(QuestionnaireItemType.TEXT, new CodeType("final")))
        .isInstanceOf(StringType.class);
    assertThat(
            DtrPrepopulationService.answerValue(
                QuestionnaireItemType.OPENCHOICE, new Coding().setCode("x")))
        .isInstanceOf(Coding.class);
    assertThatThrownBy(
            () ->
                DtrPrepopulationService.answerValue(
                    QuestionnaireItemType.ATTACHMENT, new StringType("x")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void givenPatient() {
    Patient patient = new Patient();
    patient.setId("http://localhost:8080/fhir/Patient/" + PATIENT_ID + "/_history/3");
    patient.addName().setFamily("Okafor").addGiven("Sandra");
    patient.setBirthDateElement(new DateType("1980-02-19"));
    when(patientService.findPatient(PATIENT_ID)).thenReturn(patient);
  }

  private void givenSandrasChart() {
    givenPatient();
    Condition backPain = condition("128196005", "Lumbar radiculopathy", "2026-10-05");
    Condition gallstones = condition("271807003", "Cholelithiasis", "2026-09-17");
    fhir.givenSearchByUrl(CONDITIONS, bundleOf(gallstones, backPain));
    fhir.givenSearchByUrl(
        MEDICATIONS,
        bundleOf(
            medication("Naproxen 500mg", "active", "2026-10-13"),
            medication("Ibuprofen 400mg", "stopped", "2026-10-15")));
    fhir.givenSearchByUrl(
        ENCOUNTERS,
        bundleOf(
            encounter("Low back pain radiating to the left leg", "2026-10-13"),
            encounter("Physical therapy — lumbar spine", "2026-10-17"),
            encounter("Physical therapy — lumbar spine", "2026-10-24"),
            encounter("Physical therapy — lumbar spine", "2026-10-31"),
            encounter("Physical therapy — lumbar spine", "2026-11-10")));
    fhir.givenSearchByUrl(
        REPORTS,
        bundleOf(
            report("X-ray lumbar spine, 2 views", "2026-10-14T09:30:00Z"),
            report("Surgical Pathology — Gallbladder", "2026-10-07T12:00:00Z")));
  }

  private static Condition condition(String code, String display, String onset) {
    Condition condition = new Condition();
    condition.setSubject(new Reference("Patient/" + PATIENT_ID));
    condition.setCode(new CodeableConcept().addCoding(new Coding().setCode(code)).setText(display));
    condition.setOnset(new DateTimeType(onset));
    return condition;
  }

  private static MedicationStatement medication(String name, String status, String start) {
    MedicationStatement medication = new MedicationStatement();
    medication.setStatus(MedicationStatement.MedicationStatementStatus.fromCode(status));
    medication.setMedication(new CodeableConcept().setText(name));
    medication.setEffective(new Period().setStartElement(new DateTimeType(start)));
    return medication;
  }

  private static Encounter encounter(String reason, String date) {
    Encounter encounter = new Encounter();
    encounter.setStatus(Encounter.EncounterStatus.FINISHED);
    encounter.addReasonCode().setText(reason);
    encounter.setPeriod(new Period().setStartElement(new DateTimeType(date)));
    return encounter;
  }

  private static DiagnosticReport report(String title, String issued) {
    DiagnosticReport report = new DiagnosticReport();
    report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);
    report.setCode(new CodeableConcept().setText(title));
    report.setIssuedElement(new InstantType(issued));
    return report;
  }

  private static Questionnaire mriQuestionnaire() {
    try (InputStream in =
        DtrPrepopulationServiceTest.class.getResourceAsStream(
            "/fixtures/davinci/dtr/questionnaire-mri-lumbar-spine.json")) {
      return FhirContext.forR4Cached().newJsonParser().parseResource(Questionnaire.class, in);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Questionnaire questionnaire(QuestionnaireItemComponent... items) {
    Questionnaire questionnaire = new Questionnaire();
    questionnaire.setId("test");
    questionnaire.setUrl("http://praxishealthplan.example/fhir/Questionnaire/test");
    Extension launchContext =
        questionnaire.addExtension().setUrl(DtrPrepopulationService.LAUNCH_CONTEXT);
    launchContext.addExtension(
        "name", new Coding("http://hl7.org/fhir/uv/sdc/CodeSystem/launchContext", "patient", null));
    launchContext.addExtension("type", new CodeType("Patient"));
    for (QuestionnaireItemComponent item : items) {
      questionnaire.addItem(item);
    }
    return questionnaire;
  }

  private static QuestionnaireItemComponent question(
      String linkId, QuestionnaireItemType type, String initialExpression) {
    QuestionnaireItemComponent item =
        new QuestionnaireItemComponent().setLinkId(linkId).setType(type);
    item.addExtension(
        DtrPrepopulationService.INITIAL_EXPRESSION, fhirPath(null, initialExpression));
    return item;
  }

  private static Expression fhirPath(String name, String expression) {
    return new Expression()
        .setName(name)
        .setLanguage(DtrExpressionEvaluator.FHIRPATH)
        .setExpression(expression);
  }

  private static List<String> answered(QuestionnaireResponse response) {
    return questions(response.getItem()).stream()
        .filter(QuestionnaireResponseItemComponent::hasAnswer)
        .map(QuestionnaireResponseItemComponent::getLinkId)
        .toList();
  }

  private static List<QuestionnaireResponseItemComponent> questions(
      List<QuestionnaireResponseItemComponent> items) {
    List<QuestionnaireResponseItemComponent> questions = new ArrayList<>();
    for (QuestionnaireResponseItemComponent item : items) {
      if (item.hasItem()) {
        questions.addAll(questions(item.getItem()));
      } else {
        questions.add(item);
      }
    }
    return questions;
  }
}
