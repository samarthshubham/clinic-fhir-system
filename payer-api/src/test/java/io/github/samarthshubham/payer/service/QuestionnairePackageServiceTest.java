package io.github.samarthshubham.payer.service;

import static io.github.samarthshubham.payer.support.GoldenFhir.fixture;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.samarthshubham.payer.support.FhirClientStub;
import java.util.Date;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.DeviceRequest;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class QuestionnairePackageServiceTest {

  private static final String BASE = "http://praxishealthplan.example/fhir/";
  private static final String PSG = BASE + "Questionnaire/polysomnography";
  private static final String MRI = BASE + "Questionnaire/mri-lumbar-spine";
  private static final String SNOMED = "http://snomed.info/sct";
  private static final String CPT = "http://www.ama-assn.org/go/cpt";

  private final FhirClientStub payerHapi = new FhirClientStub();
  private final QuestionnairePackageService service =
      new QuestionnairePackageService(
          payerHapi.client(),
          new CoverageRuleService(new ClassPathResource("coverage-rules.yaml")));

  private Questionnaire polysomnography;
  private Questionnaire mri;

  @BeforeEach
  void storeThePolysomnographyAndMriPackages() {
    polysomnography = fixture(Questionnaire.class, "dtr/questionnaire-polysomnography.json");
    mri = fixture(Questionnaire.class, "dtr/questionnaire-mri-lumbar-spine.json");
    payerHapi.givenSearchResult(Questionnaire.class, FhirClientStub.bundleOf(polysomnography, mri));
    payerHapi.givenSearchResult(
        Library.class,
        FhirClientStub.bundleOf(
            fixture(Library.class, "dtr/library-polysomnography.json"),
            fixture(Library.class, "dtr/library-mri-lumbar-spine.json")));
    payerHapi.givenSearchResult(
        ValueSet.class,
        FhirClientStub.bundleOf(
            fixture(ValueSet.class, "dtr/valueset-sleep-study-comorbidities.json"),
            fixture(ValueSet.class, "dtr/valueset-lumbar-spine-indications.json")));
  }

  @Test
  void polysomnographyOrderWithCptAndSnomedGetsItsPackage() {
    ServiceRequest order = order("psg-1", coding(CPT, "95810"), coding(SNOMED, "60554003"));

    Parameters output = packages(List.of(order), List.of(), null);

    Bundle bundle = onlyPackage(output);
    assertThat(bundle.getEntry())
        .extracting(entry -> entry.getResource().fhirType() + "/" + entry.getResource().getIdPart())
        .containsExactly(
            "Questionnaire/polysomnography",
            "QuestionnaireResponse/" + bundle.getEntry().get(1).getResource().getIdPart(),
            "Library/polysomnography-prepopulation",
            "ValueSet/sleep-study-comorbidities");
    assertThat(payerHapi.lastSearchCriteria(Questionnaire.class))
        .containsExactly("url=" + PSG, "_count=100");
    assertThat(payerHapi.lastSearchCriteria(Library.class))
        .containsExactly("url=" + BASE + "Library/polysomnography-prepopulation", "_count=100");
    assertThat(payerHapi.lastSearchCriteria(ValueSet.class))
        .containsExactly("url=" + BASE + "ValueSet/sleep-study-comorbidities", "_count=100");
    assertThat(output.getParameter("outcome")).isNull();
  }

  @Test
  void emptyResponseIsForTheMemberCoverageAndOrder() {
    Parameters output =
        packages(List.of(order("psg-1", coding(SNOMED, "60554003"))), List.of(), null);

    QuestionnaireResponse response =
        (QuestionnaireResponse) onlyPackage(output).getEntry().get(1).getResource();
    assertThat(response.getStatus())
        .isEqualTo(QuestionnaireResponse.QuestionnaireResponseStatus.INPROGRESS);
    assertThat(response.getQuestionnaire()).isEqualTo(PSG);
    assertThat(response.getSubject().getReference()).isEqualTo("Patient/gerald-horton");
    assertThat(
            ((Reference)
                    response.getExtensionByUrl(QuestionnairePackageService.QR_COVERAGE).getValue())
                .getReference())
        .isEqualTo("Coverage/coverage-1");
    assertThat(
            ((Reference)
                    response.getExtensionByUrl(QuestionnairePackageService.QR_CONTEXT).getValue())
                .getReference())
        .isEqualTo("ServiceRequest/psg-1");
    assertThat(response.getItem()).isEmpty();
  }

  @Test
  void samePackageRequestGetsTheSameResponseId() {
    ServiceRequest order = order("psg-1", coding(SNOMED, "60554003"));

    String first = responseId(packages(List.of(order), List.of(), null));
    String second = responseId(packages(List.of(order), List.of(), null));

    assertThat(first).isEqualTo(second);
    assertThat(
            onlyPackage(packages(List.of(order), List.of(), null)).getEntry().get(1).getFullUrl())
        .isEqualTo("urn:uuid:" + first);
  }

  @Test
  void ordersAndRequestedCanonicalsForTheSameQuestionnaireShareOnePackage() {
    Parameters output =
        packages(
            List.of(
                order("psg-1", coding(SNOMED, "60554003")),
                order("psg-2", coding(SNOMED, "60554003"))),
            List.of(PSG),
            null);

    QuestionnaireResponse response =
        (QuestionnaireResponse) onlyPackage(output).getEntry().get(1).getResource();
    assertThat(response.getExtensionsByUrl(QuestionnairePackageService.QR_CONTEXT)).hasSize(2);
  }

  @Test
  void twoOrdersGetTwoPackages() {
    Parameters output =
        packages(
            List.of(
                order("psg-1", coding(SNOMED, "60554003")), order("mri-1", coding(CPT, "72148"))),
            List.of(),
            null);

    assertThat(output.getParameter())
        .extracting(Parameters.ParametersParameterComponent::getName)
        .containsExactly("packagebundle", "packagebundle");
    assertThat(payerHapi.lastSearchCriteria(Questionnaire.class))
        .containsExactly("url=" + PSG + "," + MRI, "_count=100");
  }

  @Test
  void deviceAndMedicationRequestsAreReadForTheirCodes() {
    DeviceRequest device = new DeviceRequest();
    device.setId("dr-1");
    device.setCode(new CodeableConcept(coding(SNOMED, "60554003")));
    MedicationRequest medication = new MedicationRequest();
    medication.setMedication(new Reference("Medication/m1"));

    Parameters output = packages(List.of(device, medication), List.of(), null);

    assertThat(output.getParameter("packagebundle")).isNotNull();
    assertThat(output.getParameter()).hasSize(1);
  }

  @Test
  void ordersThatNeedNoDocumentationGetOnlyAnInformationOutcome() {
    Parameters output =
        packages(List.of(order("cbc-1", coding("http://loinc.org", "58410-2"))), List.of(), null);

    assertThat(output.getParameter())
        .extracting(Parameters.ParametersParameterComponent::getName)
        .containsExactly("outcome");
    assertThat(issue(output).getSeverity()).isEqualTo(OperationOutcome.IssueSeverity.INFORMATION);
    assertThat(payerHapi.searchCount(Questionnaire.class)).isZero();
  }

  @Test
  void unknownQuestionnaireGetsANotFoundWarning() {
    Parameters output = packages(List.of(), List.of(BASE + "Questionnaire/unknown"), null);

    assertThat(output.getParameter("packagebundle")).isNull();
    assertThat(issue(output).getCode()).isEqualTo(OperationOutcome.IssueType.NOTFOUND);
    assertThat(issue(output).getDiagnostics()).contains("Questionnaire/unknown");
  }

  @Test
  void versionedCanonicalMatchesOnlyThatVersion() {
    polysomnography.setVersion("2.0.0");

    assertThat(packages(List.of(), List.of(PSG + "|2.0.0"), null).getParameter("packagebundle"))
        .isNotNull();
    assertThat(packages(List.of(), List.of(PSG + "|1.0.0"), null).getParameter("packagebundle"))
        .isNull();
    assertThat(payerHapi.lastSearchCriteria(Questionnaire.class))
        .containsExactly("url=" + PSG, "_count=100");
  }

  @Test
  void versionedQuestionnaireIsNamedWithItsVersionInTheResponse() {
    polysomnography.setVersion("2.0.0");

    QuestionnaireResponse response =
        (QuestionnaireResponse)
            onlyPackage(packages(List.of(), List.of(PSG), null)).getEntry().get(1).getResource();

    assertThat(response.getQuestionnaire()).isEqualTo(PSG + "|2.0.0");
  }

  @Test
  void missingLibraryOrValueSetIsWarnedAndLeftOut() {
    payerHapi.givenSearchResult(Library.class, new Bundle());

    Parameters output = packages(List.of(), List.of(PSG), null);

    assertThat(onlyPackage(output).getEntry())
        .extracting(entry -> entry.getResource().fhirType())
        .containsExactly("Questionnaire", "QuestionnaireResponse", "ValueSet");
    assertThat(issue(output).getDiagnostics()).contains("Library/polysomnography-prepopulation");
  }

  @Test
  void storedVersionDetailsAreNotSentInThePackage() {
    polysomnography.setId("http://localhost:8081/fhir/Questionnaire/polysomnography/_history/3");
    polysomnography.getMeta().setVersionId("3").setLastUpdated(new Date()).setSource("#abc");

    Questionnaire sent =
        (Questionnaire)
            onlyPackage(packages(List.of(), List.of(PSG), null)).getEntryFirstRep().getResource();

    assertThat(sent.getId()).isEqualTo("polysomnography");
    assertThat(sent.getMeta().hasVersionId()).isFalse();
    assertThat(sent.getMeta().hasLastUpdated()).isFalse();
    assertThat(sent.getMeta().hasSource()).isFalse();
    assertThat(sent.getMeta().getProfile()).isNotEmpty();
  }

  @Test
  void changedSinceLeavesOutPackagesThatHaveNotChanged() {
    Date lastWeek = new Date(System.currentTimeMillis() - 7L * 24 * 3600 * 1000);
    Date yesterday = new Date(System.currentTimeMillis() - 24L * 3600 * 1000);
    Date now = new Date();
    stampLastUpdated(lastWeek);

    assertThat(packages(List.of(), List.of(PSG), yesterday).getParameter("packagebundle")).isNull();

    polysomnography.getMeta().setLastUpdated(now);
    assertThat(packages(List.of(), List.of(PSG), yesterday).getParameter("packagebundle"))
        .isNotNull();
  }

  private void stampLastUpdated(Date date) {
    polysomnography.getMeta().setLastUpdated(date);
    Library library = fixture(Library.class, "dtr/library-polysomnography.json");
    library.getMeta().setLastUpdated(date);
    ValueSet valueSet = fixture(ValueSet.class, "dtr/valueset-sleep-study-comorbidities.json");
    valueSet.getMeta().setLastUpdated(date);
    payerHapi.givenSearchResult(Library.class, FhirClientStub.bundleOf(library));
    payerHapi.givenSearchResult(ValueSet.class, FhirClientStub.bundleOf(valueSet));
  }

  private Parameters packages(List<Resource> orders, List<String> canonicals, Date changedSince) {
    payerHapi.givenSearchResult(Questionnaire.class, FhirClientStub.bundleOf(polysomnography, mri));
    return service.questionnairePackages(coverage(), orders, canonicals, changedSince);
  }

  private static Bundle onlyPackage(Parameters output) {
    List<Parameters.ParametersParameterComponent> packages =
        output.getParameter().stream().filter(p -> p.getName().equals("packagebundle")).toList();
    assertThat(packages).hasSize(1);
    return (Bundle) packages.get(0).getResource();
  }

  private static String responseId(Parameters output) {
    return onlyPackage(output).getEntry().get(1).getResource().getIdPart();
  }

  private static OperationOutcome.OperationOutcomeIssueComponent issue(Parameters output) {
    return ((OperationOutcome) output.getParameter("outcome").getResource()).getIssueFirstRep();
  }

  private static Coverage coverage() {
    Coverage coverage = new Coverage();
    coverage.setId("coverage-1");
    coverage.setBeneficiary(new Reference("Patient/gerald-horton"));
    return coverage;
  }

  private static ServiceRequest order(String id, Coding... codings) {
    ServiceRequest order = new ServiceRequest();
    order.setId(id);
    CodeableConcept code = new CodeableConcept();
    for (Coding coding : codings) {
      code.addCoding(coding);
    }
    order.setCode(code);
    return order;
  }

  private static Coding coding(String system, String code) {
    return new Coding(system, code, null);
  }
}
