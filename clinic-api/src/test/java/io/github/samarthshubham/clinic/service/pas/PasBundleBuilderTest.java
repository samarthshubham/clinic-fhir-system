package io.github.samarthshubham.clinic.service.pas;

import static io.github.samarthshubham.clinic.support.GoldenFhir.assertMatchesGolden;
import static io.github.samarthshubham.clinic.support.GoldenFhir.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.context.FhirContext;
import io.github.samarthshubham.clinic.client.CrdClient;
import io.github.samarthshubham.clinic.exception.PasRequestException;
import io.github.samarthshubham.clinic.mapper.CoverageMapper;
import io.github.samarthshubham.clinic.mapper.DocumentationMapper;
import io.github.samarthshubham.clinic.mapper.PractitionerMapper;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.SimpleQuantity;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class PasBundleBuilderTest {

  private static final FhirContext FHIR = FhirContext.forR4Cached();
  private static final String BASE = "http://praxis.clinic/fhir";
  private static final String ORDER = "mri-lumbar-spine-sandra-okafor";
  private static final String PATIENT = "sandra-okafor";
  private static final String COVERAGE = "coverage-sandra-okafor";
  private static final String PRACTITIONER = "aisha-patel";
  private static final String QUESTIONNAIRE =
      "http://praxishealthplan.example/fhir/Questionnaire/mri-lumbar-spine";
  private static final String CPT = "http://www.ama-assn.org/go/cpt";
  private static final String THO = "http://terminology.hl7.org/CodeSystem/";
  private static final String X12 = "https://codesystem.x12.org/005010/1365";
  private static final DateTimeType SUBMITTED = new DateTimeType("2026-08-20T10:15:00-05:00");

  private final PasBundleBuilder builder = new PasBundleBuilder(FHIR);

  @Test
  void mriBundleMatchesGolden() {
    PasBundleBuilder.Request input = mri();
    String chartOrder = FHIR.newJsonParser().encodeResourceToString(input.order());
    String chartResponse = FHIR.newJsonParser().encodeResourceToString(input.response());

    assertMatchesGolden(build(input), "pas/request-mri.json");

    assertThat(FHIR.newJsonParser().encodeResourceToString(input.order())).isEqualTo(chartOrder);
    assertThat(FHIR.newJsonParser().encodeResourceToString(input.response()))
        .isEqualTo(chartResponse);
  }

  @Test
  void retriesKeepTheSameClaimIdentifierAndDifferentOrdersGetDifferentIdentifiers() {
    PasBundleBuilder.Request input = mri();

    Bundle original = build(input);
    Claim first = claim(original);
    Bundle retried =
        builder.build(input, billing(), BASE, new DateTimeType("2026-08-20T10:20:00-05:00"));
    Claim retry = claim(retried);
    input.order().setId("a-later-order");
    input
        .response()
        .getExtensionByUrl(DocumentationMapper.QR_CONTEXT)
        .setValue(new Reference("ServiceRequest/a-later-order"));
    Claim second = claim(build(input));

    assertThat(retry.getIdentifierFirstRep().equalsDeep(first.getIdentifierFirstRep())).isTrue();
    assertThat(retried.getEntryFirstRep().getFullUrl())
        .isEqualTo(original.getEntryFirstRep().getFullUrl());
    assertThat(retry.getCreated()).isAfter(first.getCreated());
    assertThat(second.getIdentifierFirstRep().getValue())
        .isNotEqualTo(first.getIdentifierFirstRep().getValue());
  }

  @ParameterizedTest
  @EnumSource(
      value = ServiceRequest.ServiceRequestPriority.class,
      names = {"URGENT", "ASAP", "STAT"})
  void expeditedOrdersUseStatWithoutChangingTheOriginalOrderPriority(
      ServiceRequest.ServiceRequestPriority priority) {
    var input = mri();
    input.order().setPriority(priority);

    Bundle result = build(input);

    assertThat(claim(result).getPriority().getCodingFirstRep().getCode()).isEqualTo("stat");
    assertThat(resource(result, ServiceRequest.class).getPriority()).isEqualTo(priority);
  }

  @Test
  void canBuildWithoutAnAttachmentIfCrdDidNotRequestOne() {
    var input = mri();
    Extension assertion = input.order().getExtensionByUrl(CrdClient.COVERAGE_INFORMATION);
    assertion
        .getExtension()
        .removeIf(
            extension ->
                List.of("doc-needed", "reason", "questionnaire").contains(extension.getUrl()));
    input.order().setPriority(null);
    var noDocumentation =
        new PasBundleBuilder.Request(
            input.order(),
            input.patient(),
            input.coverage(),
            input.practitioner(),
            input.requestor(),
            input.insurer(),
            null,
            List.of());

    Bundle bundle = build(noDocumentation);

    assertThat(claim(bundle).getSupportingInfo()).isEmpty();
    assertThat(claim(bundle).getPriority().getCodingFirstRep().getCode()).isEqualTo("normal");
    assertThat(bundle.getEntry())
        .noneMatch(entry -> entry.getResource() instanceof QuestionnaireResponse);
  }

  @Test
  void omitsClaimDiagnosesAndSequencesWhenBillingDiagnosesAreMissing() {
    var input = mri();
    var billing = billing();
    var omitted =
        new PasBundleBuilder.Billing(
            billing.service(), null, billing.category(), billing.placeOfService());
    var empty =
        new PasBundleBuilder.Billing(
            billing.service(), List.of(), billing.category(), billing.placeOfService());

    Bundle result = builder.build(input, omitted, BASE, SUBMITTED);
    Claim claim = claim(result);

    assertThat(claim.getDiagnosis()).isEmpty();
    assertThat(claim.getItemFirstRep().getDiagnosisSequence()).isEmpty();
    assertThat(result.equalsDeep(builder.build(input, empty, BASE, SUBMITTED))).isTrue();
  }

  @Test
  void usesExplicitPasBillingCodeForAClinicallyCodedOrder() {
    var input = mri();
    input
        .order()
        .getCode()
        .setCoding(List.of(new Coding("http://snomed.info/sct", "60554003", null)));
    var billing =
        new PasBundleBuilder.Billing(
            new Coding(CPT, "95810", null), List.of(), new Coding(X12, "1", "Medical Care"), "11");

    Bundle bundle = builder.build(input, billing, BASE, SUBMITTED);

    assertThat(claim(bundle).getItemFirstRep().getProductOrService().getCodingFirstRep().getCode())
        .isEqualTo("95810");
    assertThat(resource(bundle, ServiceRequest.class).getCode().getCodingFirstRep().getCode())
        .isEqualTo("95810");
  }

  @ParameterizedTest
  @MethodSource("invalidInputs")
  void rejectsInvalidOrderContext(Consumer<PasBundleBuilder.Request> change, String error) {
    var input = mri();
    change.accept(input);

    assertThatThrownBy(() -> build(input))
        .isInstanceOf(PasRequestException.class)
        .hasMessageContaining(error);
  }

  static Stream<Arguments> invalidInputs() {
    return Stream.of(
        invalid(input -> input.practitioner().getIdentifier().clear(), "must have an NPI"),
        invalid(input -> input.requestor().getTelecom().clear(), "needs a work contact for PAS"),
        invalid(
            input ->
                input.order().setQuantity(new Range().setLow(new SimpleQuantity().setValue(1))),
            "order quantity as a Quantity"),
        invalid(
            input -> input.order().getSubject().setReference("Patient/another-patient"),
            "must match"),
        invalid(
            input -> input.coverage().getPayorFirstRep().setReference("Organization/another-payer"),
            "must match"),
        invalid(input -> input.order().getExtension().clear(), "needs a CRD coverage assertion"),
        invalid(
            input ->
                input
                    .order()
                    .getExtensionByUrl(CrdClient.COVERAGE_INFORMATION)
                    .getExtensionByUrl("coverage")
                    .setValue(new Reference("Coverage/old")),
            "selected coverage"),
        invalid(
            input ->
                input
                    .response()
                    .setStatus(QuestionnaireResponse.QuestionnaireResponseStatus.INPROGRESS),
            "Complete the payer documentation"),
        invalid(
            input ->
                input
                    .response()
                    .getExtensionByUrl(DocumentationMapper.QR_COVERAGE)
                    .setValue(new Reference("Coverage/old")),
            "Complete the payer documentation"),
        invalid(
            input -> input.response().setQuestionnaire("http://example.org/different-form"),
            "Complete the payer documentation"),
        invalid(input -> input.order().getCode().getCoding().clear(), "needs a PAS billing code"),
        invalid(
            input -> {
              input.coverage().getIdentifier().clear();
              input.coverage().setSubscriberId(null);
            },
            "needs a member identifier"),
        invalid(
            input -> input.coverage().getRelationship().getCodingFirstRep().setCode("spouse"),
            "requires subscriber details"),
        invalid(
            input ->
                input
                    .response()
                    .setAuthor(
                        new Reference("https://other.example/fhir/Practitioner/" + PRACTITIONER)),
            "outside the selected order"));
  }

  @Test
  void rejectsRequiredButMissingDocumentation() {
    var input = mri();
    var missing =
        new PasBundleBuilder.Request(
            input.order(),
            input.patient(),
            input.coverage(),
            input.practitioner(),
            input.requestor(),
            input.insurer(),
            null,
            List.of());

    assertThatThrownBy(() -> build(missing))
        .isInstanceOf(PasRequestException.class)
        .hasMessageContaining("Complete the payer documentation");
  }

  @Test
  void requiresBillingCategoryAndPlaceOfService() {
    var missing = new PasBundleBuilder.Billing(null, List.of(), null, null);
    assertThatThrownBy(() -> builder.build(mri(), missing, BASE, SUBMITTED))
        .isInstanceOf(PasRequestException.class)
        .hasMessageContaining("requires a service category and a place of service");
  }

  static PasBundleBuilder.Billing billing() {
    return new PasBundleBuilder.Billing(
        null,
        List.of(
            new Coding(
                "http://hl7.org/fhir/sid/icd-10-cm", "M54.16", "Radiculopathy, lumbar region")),
        new Coding(X12, "1", "Medical Care"),
        "11");
  }

  static PasBundleBuilder.Request mri() {
    var order =
        new ServiceRequest()
            .setStatus(ServiceRequest.ServiceRequestStatus.ACTIVE)
            .setIntent(ServiceRequest.ServiceRequestIntent.ORDER)
            .setPriority(ServiceRequest.ServiceRequestPriority.ROUTINE)
            .setCode(
                new CodeableConcept(new Coding(CPT, "72148", null))
                    .setText("MRI lumbar spine without contrast"))
            .setSubject(new Reference("Patient/" + PATIENT))
            .setRequester(new Reference("Practitioner/" + PRACTITIONER));
    order.setId(ORDER);
    Extension assertion = order.addExtension().setUrl(CrdClient.COVERAGE_INFORMATION);
    assertion.addExtension("coverage", new Reference("Coverage/" + COVERAGE));
    assertion.addExtension("covered", new CodeType("covered"));
    assertion.addExtension("pa-needed", new CodeType("auth-needed"));
    assertion.addExtension("doc-needed", new CodeType("clinical"));
    assertion.addExtension(
        "reason",
        new CodeableConcept()
            .setText("Clinical documentation is needed for the prior authorization request"));
    assertion.addExtension("questionnaire", new CanonicalType(QUESTIONNAIRE));
    assertion.addExtension("date", new DateType("2026-08-20"));
    assertion.addExtension(
        "coverage-assertion-id", new StringType("a9ca32f9-f406-36e8-a145-1768cb763da1"));

    Patient patient = new Patient();
    patient.setId(PATIENT);
    patient.addName().setFamily("Okafor").addGiven("Sandra");
    patient.setGender(AdministrativeGender.FEMALE);
    patient.setBirthDateElement(new DateType("1981-03-14"));
    patient.addIdentifier().setSystem(BASE + "/identifier/mrn").setValue("PX-10000005");
    patient.addTelecom().setValue("5551004004");

    Practitioner practitioner = new Practitioner();
    practitioner.setId(PRACTITIONER);
    practitioner.addName().setFamily("Patel").addGiven("Aisha").addPrefix("Dr.");
    practitioner.addIdentifier().setSystem(PractitionerMapper.NPI_SYSTEM).setValue("1234567893");
    practitioner.addIdentifier().setSystem(PractitionerMapper.USERNAME_SYSTEM).setValue("aisha");
    practitioner.addQualification().setCode(new CodeableConcept().setText("Internal medicine"));

    Organization requestor = new Organization().setName("Praxis Clinic").setActive(true);
    requestor.setId("praxis-clinic");
    requestor.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue("5550100100");
    requestor.addIdentifier().setSystem(PractitionerMapper.NPI_SYSTEM).setValue("1987654328");
    requestor.addAddress(
        new Address()
            .addLine("100 Example Way")
            .setCity("Springfield")
            .setState("IL")
            .setPostalCode("62701")
            .setCountry("US"));

    Organization insurer = new Organization().setName("Praxis Health Plan").setActive(true);
    insurer.setId("praxis-health-plan");
    insurer
        .addIdentifier()
        .setSystem("http://praxishealthplan.example/fhir/identifier/payer-id")
        .setValue("PHP01");

    Coverage coverage =
        new Coverage()
            .setStatus(Coverage.CoverageStatus.ACTIVE)
            .setSubscriberId("PHP-10005")
            .setBeneficiary(new Reference("Patient/" + PATIENT))
            .setRelationship(
                new CodeableConcept(new Coding(THO + "subscriber-relationship", "self", null)));
    coverage.setId(COVERAGE);
    coverage.addPayor(new Reference("Organization/" + insurer.getIdPart()));
    coverage.addIdentifier().setSystem(CoverageMapper.MEMBER_ID_SYSTEM).setValue("PHP-10005");

    String dtr =
        read("dtr/qr-mri-completed.json")
            .replace("Practitioner/pr-anika-patel", "Practitioner/" + PRACTITIONER)
            .replace("Patient/seed-sandra-okafor", "Patient/" + PATIENT)
            .replace("ServiceRequest/mri-sandra-okafor", "ServiceRequest/" + ORDER);
    var response = FHIR.newJsonParser().parseResource(QuestionnaireResponse.class, dtr);
    response.setId(ORDER + "-dtr");
    response.setAuthoredElement(new DateTimeType("2026-08-20T10:10:00-05:00"));
    response.getItem().stream()
        .flatMap(group -> group.getItem().stream())
        .filter(item -> "conservative-therapy-weeks".equals(item.getLinkId()))
        .findFirst()
        .orElseThrow()
        .getAnswerFirstRep()
        .getValueIntegerType()
        .setValue(4);

    return new PasBundleBuilder.Request(
        order, patient, coverage, practitioner, requestor, insurer, response, List.of());
  }

  private static Arguments invalid(Consumer<PasBundleBuilder.Request> change, String error) {
    return Arguments.of(change, error);
  }

  private Bundle build(PasBundleBuilder.Request input) {
    return builder.build(input, billing(), BASE, SUBMITTED);
  }

  private static Claim claim(Bundle bundle) {
    return (Claim) bundle.getEntryFirstRep().getResource();
  }

  private static <T> T resource(Bundle bundle, Class<T> type) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(type::isInstance)
        .map(type::cast)
        .findFirst()
        .orElseThrow();
  }
}
