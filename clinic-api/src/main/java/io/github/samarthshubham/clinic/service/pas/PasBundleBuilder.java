package io.github.samarthshubham.clinic.service.pas;

import ca.uhn.fhir.context.FhirContext;
import io.github.samarthshubham.clinic.client.CrdClient;
import io.github.samarthshubham.clinic.exception.PasRequestException;
import io.github.samarthshubham.clinic.mapper.CoverageMapper;
import io.github.samarthshubham.clinic.mapper.DocumentationMapper;
import io.github.samarthshubham.clinic.mapper.PractitionerMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PasBundleBuilder {

  private static final String PAS = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/";
  private static final String IDENTIFIERS = "http://praxis.clinic/fhir/identifier/";
  private static final String THO = "http://terminology.hl7.org/CodeSystem/";
  private static final String X12 = "https://codesystem.x12.org/005010/";
  private static final String POS =
      "https://www.cms.gov/Medicare/Coding/place-of-service-codes/Place_of_Service_Code_Set";
  private static final Set<String> SERVICE_SYSTEMS =
      Set.of(
          X12 + "1365",
          "http://www.ama-assn.org/go/cpt",
          "http://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets",
          "http://terminology.hl7.org/CodeSystem/icd9cm",
          "http://www.cms.gov/Medicare/Coding/ICD10",
          "http://hl7.org/fhir/sid/ndc");

  private final FhirContext fhirContext;

  public PasBundleBuilder(FhirContext fhirContext) {
    this.fhirContext = fhirContext;
  }

  public record Billing(
      Coding service, List<Coding> diagnoses, Coding category, String placeOfService) {}

  record Request(
      ServiceRequest order,
      Patient patient,
      Coverage coverage,
      Practitioner practitioner,
      Organization requestor,
      Organization insurer,
      QuestionnaireResponse response,
      List<Practitioner> documentationAuthors) {}

  Bundle build(Request request, Billing billing, String providerBaseUrl, DateTimeType submittedAt) {
    validate(request, providerBaseUrl);
    validateBilling(billing);
    String orderId = request.order().getIdPart();
    String submission = "PX-PA-" + orderId;
    String claimId = generatedId(providerBaseUrl, "Claim", orderId);
    String roleId =
        generatedId(
            providerBaseUrl,
            "PractitionerRole",
            request.practitioner().getIdPart() + "/" + request.requestor().getIdPart());
    String roleUrl = "urn:uuid:" + roleId;

    Identifier member = memberIdentifier(request.coverage());
    Patient patient = subscriber(request.patient(), member);
    Organization requestor = organization(request.requestor(), "requestor", "X3");
    Organization insurer = organization(request.insurer(), "insurer", "PR");
    Coverage coverage = coverage(request.coverage(), member, patient, insurer);
    Practitioner practitioner = practitioner(request.practitioner(), "profile-practitioner");
    PractitionerRole role = practitionerRole(roleId, request, practitioner, requestor);
    Coding service = serviceCode(request.order(), billing);
    ServiceRequest order = order(request.order(), patient, service);
    QuestionnaireResponse response =
        request.response() == null ? null : documentation(request.response(), patient);

    Bundle bundle = profile(new Bundle(), null, "profile-pas-request-bundle");
    bundle.setType(Bundle.BundleType.COLLECTION);
    bundle.setIdentifier(
        new Identifier().setSystem(IDENTIFIERS + "pas-submission").setValue(submission));
    bundle.setTimestampElement(new InstantType(submittedAt.asStringValue()));

    Claim claim = claim(submission, submittedAt, order, billing, service, roleUrl);
    claim.setPatient(reference(patient));
    claim.setProvider(reference(requestor));
    claim.setInsurer(reference(insurer));
    claim.addInsurance().setSequence(1).setFocal(true).setCoverage(reference(coverage));
    if (response != null) {
      attach(claim, response);
    }

    bundle.addEntry().setFullUrl("urn:uuid:" + claimId).setResource(claim);
    for (Resource resource : List.of(requestor, insurer, coverage, patient, order)) {
      addEntry(bundle, providerBaseUrl, resource);
    }
    bundle.addEntry().setFullUrl(roleUrl).setResource(role);
    addEntry(bundle, providerBaseUrl, practitioner);
    if (response != null) {
      addEntry(bundle, providerBaseUrl, response);
      for (Practitioner author : request.documentationAuthors()) {
        addEntry(bundle, providerBaseUrl, practitioner(author, null));
      }
    }
    resolveReferences(bundle, providerBaseUrl);
    return bundle;
  }

  private static void validate(Request request, String baseUrl) {
    if (request.practitioner().getIdentifier().stream()
        .noneMatch(
            id ->
                PractitionerMapper.NPI_SYSTEM.equals(id.getSystem())
                    && StringUtils.hasText(id.getValue()))) {
      throw new PasRequestException("The ordering Practitioner must have an NPI");
    }
    if (!matches(request.order().getSubject(), request.patient(), baseUrl)
        || !matches(request.coverage().getBeneficiary(), request.patient(), baseUrl)
        || !matches(request.order().getRequester(), request.practitioner(), baseUrl)
        || !matches(request.coverage().getPayorFirstRep(), request.insurer(), baseUrl)) {
      throw new PasRequestException(
          "The order, patient, practitioner and payer coverage must match");
    }
    Extension assertion = request.order().getExtensionByUrl(CrdClient.COVERAGE_INFORMATION);
    if (assertion == null
        || !references(assertion.getExtensionByUrl("coverage"), request.coverage(), baseUrl)
        || !StringUtils.hasText(primitive(assertion, "coverage-assertion-id"))) {
      throw new PasRequestException(
          "The order needs a CRD coverage assertion for the selected coverage");
    }
    QuestionnaireResponse response = request.response();
    boolean needsDocumentation =
        StringUtils.hasText(primitive(assertion, "doc-needed"))
            || StringUtils.hasText(primitive(assertion, "questionnaire"));
    if (response == null && !needsDocumentation) {
      return;
    }
    if (response == null
        || response.getStatus() != QuestionnaireResponse.QuestionnaireResponseStatus.COMPLETED
        || !response.hasQuestionnaire()
        || !response.hasItem()
        || !matches(response.getSubject(), request.patient(), baseUrl)
        || !references(
            response.getExtensionByUrl(DocumentationMapper.QR_COVERAGE),
            request.coverage(),
            baseUrl)
        || response.getExtensionsByUrl(DocumentationMapper.QR_CONTEXT).stream()
            .noneMatch(context -> references(context, request.order(), baseUrl))
        || !sameQuestionnaire(primitive(assertion, "questionnaire"), response.getQuestionnaire())) {
      throw new PasRequestException(
          "Complete the payer documentation for this order and coverage before submitting");
    }
  }

  private static void validateBilling(Billing billing) {
    if (billing == null
        || billing.category() == null
        || !billing.category().hasCode()
        || !StringUtils.hasText(billing.placeOfService())) {
      throw new PasRequestException("PAS requires a service category and a place of service");
    }
  }

  private static boolean sameQuestionnaire(String expected, String actual) {
    return !StringUtils.hasText(expected)
        || (actual != null && (actual.equals(expected) || actual.startsWith(expected + "|")));
  }

  private static String primitive(Extension parent, String url) {
    Extension child = parent.getExtensionByUrl(url);
    return child != null && child.getValue() instanceof PrimitiveType<?> value
        ? value.getValueAsString()
        : null;
  }

  private static boolean references(Extension extension, Resource resource, String baseUrl) {
    return extension != null
        && extension.getValue() instanceof Reference reference
        && matches(reference, resource, baseUrl);
  }

  private static boolean matches(Reference reference, Resource resource, String baseUrl) {
    var id = reference.getReferenceElement();
    return (!id.hasBaseUrl() || baseUrl.equals(id.getBaseUrl()))
        && (resource.fhirType() + "/" + resource.getIdPart())
            .equals(id.toUnqualifiedVersionless().getValue());
  }

  private static Coding serviceCode(ServiceRequest order, Billing billing) {
    Coding billingCode =
        order.getCode().getCoding().stream()
            .filter(PasBundleBuilder::isPasServiceCode)
            .findFirst()
            .orElse(billing.service());
    if (!isPasServiceCode(billingCode)) {
      throw new PasRequestException("The order needs a PAS billing code for the requested service");
    }
    return new Coding(billingCode.getSystem(), billingCode.getCode(), billingCode.getDisplay());
  }

  private static boolean isPasServiceCode(Coding coding) {
    return coding != null
        && coding.hasSystem()
        && coding.hasCode()
        && SERVICE_SYSTEMS.contains(coding.getSystem());
  }

  private static Identifier memberIdentifier(Coverage coverage) {
    String memberId =
        coverage.getIdentifier().stream()
            .filter(id -> CoverageMapper.MEMBER_ID_SYSTEM.equals(id.getSystem()) && id.hasValue())
            .map(Identifier::getValue)
            .findFirst()
            .orElse(coverage.getSubscriberId());
    if (!StringUtils.hasText(memberId)) {
      throw new PasRequestException("The selected coverage needs a member identifier");
    }
    return new Identifier()
        .setSystem(CoverageMapper.MEMBER_ID_SYSTEM)
        .setValue(memberId)
        .setType(concept(THO + "v2-0203", "MB", null));
  }

  private static Patient subscriber(Patient source, Identifier member) {
    Patient patient = profile(new Patient(), source.getIdPart(), "profile-subscriber");
    patient.addIdentifier(member.copy());
    patient.setName(source.getName().stream().map(name -> name.copy()).toList());
    patient.setGender(source.hasGender() ? source.getGender() : AdministrativeGender.UNKNOWN);
    patient.setBirthDateElement(source.getBirthDateElement().copy());
    return patient;
  }

  private static Coverage coverage(
      Coverage source, Identifier member, Patient patient, Organization insurer) {
    if (!source.getRelationship().getCoding().stream()
        .anyMatch(
            coding ->
                (THO + "subscriber-relationship").equals(coding.getSystem())
                    && "self".equals(coding.getCode()))) {
      throw new PasRequestException(
          "PAS requires subscriber details for coverage held by someone other than the patient");
    }
    Coverage coverage = profile(new Coverage(), source.getIdPart(), "profile-coverage");
    coverage.addIdentifier(member.copy());
    coverage
        .setStatus(source.getStatus())
        .setSubscriberId(member.getValue())
        .setBeneficiary(reference(patient));
    coverage.setRelationship(
        concept(THO + "subscriber-relationship", "self", null)
            .addCoding(new Coding(X12 + "1069", "18", null)));
    coverage.addPayor(reference(insurer));
    coverage.setPeriod(source.getPeriod().copy());
    coverage.setClass_(source.getClass_().stream().map(Coverage.ClassComponent::copy).toList());
    return coverage;
  }

  private static Organization organization(Organization source, String role, String type) {
    Organization organization = profile(new Organization(), source.getIdPart(), "profile-" + role);
    organization.setActive(source.hasActive() && source.getActive()).setName(source.getName());
    organization.setIdentifier(source.getIdentifier().stream().map(Identifier::copy).toList());
    organization.setAddress(source.getAddress().stream().map(address -> address.copy()).toList());
    organization.addType(concept(X12 + "98", type, null));
    return organization;
  }

  private static Practitioner practitioner(Practitioner source, String pasProfile) {
    Practitioner practitioner = profile(new Practitioner(), source.getIdPart(), pasProfile);
    if (pasProfile == null) {
      practitioner
          .getMeta()
          .addProfile("http://hl7.org/fhir/us/core/StructureDefinition/us-core-practitioner");
    }
    source.getIdentifier().stream()
        .filter(id -> PractitionerMapper.NPI_SYSTEM.equals(id.getSystem()) && id.hasValue())
        .forEach(
            id ->
                practitioner.addIdentifier(
                    new Identifier().setSystem(id.getSystem()).setValue(id.getValue())));
    if (!practitioner.hasIdentifier()) {
      practitioner
          .addIdentifier()
          .setSystem(IDENTIFIERS + "practitioner")
          .setValue(source.getIdPart());
    }
    practitioner.setName(source.getName().stream().map(name -> name.copy()).toList());
    return practitioner;
  }

  private static PractitionerRole practitionerRole(
      String id, Request source, Practitioner practitioner, Organization requestor) {
    ContactPoint contact =
        Stream.concat(
                source.requestor().getTelecom().stream(),
                source.practitioner().getTelecom().stream())
            .filter(point -> point.hasSystem() && point.hasValue())
            .filter(point -> !point.hasUse() || point.getUse() == ContactPoint.ContactPointUse.WORK)
            .findFirst()
            .orElseThrow(
                () ->
                    new PasRequestException(
                        "The ordering Practitioner or submitting clinic needs a work contact for PAS"));
    PractitionerRole role = profile(new PractitionerRole(), null, "profile-practitionerrole");
    role.addIdentifier().setSystem(IDENTIFIERS + "practitioner-role").setValue(id);
    role.setPractitioner(reference(practitioner));
    role.setOrganization(reference(requestor));
    role.addTelecom(contact.copy());
    return role;
  }

  private static ServiceRequest order(ServiceRequest source, Patient patient, Coding service) {
    ServiceRequest order =
        profile(new ServiceRequest(), source.getIdPart(), "profile-servicerequest");
    order
        .setStatus(source.getStatus())
        .setIntent(source.getIntent())
        .setSubject(reference(patient));
    order.setPriority(
        source.hasPriority()
            ? source.getPriority()
            : ServiceRequest.ServiceRequestPriority.ROUTINE);
    order.setCode(new CodeableConcept(service.copy()).setText(source.getCode().getText()));
    if (source.hasQuantity() && !(source.getQuantity() instanceof Quantity)) {
      throw new PasRequestException("PAS requires the order quantity as a Quantity");
    }
    order.setQuantity(
        source.hasQuantity()
            ? source.getQuantity().copy()
            : new Quantity()
                .setValue(1)
                .setUnit("each")
                .setSystem("http://unitsofmeasure.org")
                .setCode("1"));
    order.addExtension(source.getExtensionByUrl(CrdClient.COVERAGE_INFORMATION).copy());
    return order;
  }

  private static QuestionnaireResponse documentation(
      QuestionnaireResponse source, Patient patient) {
    QuestionnaireResponse response = profile(new QuestionnaireResponse(), source.getIdPart(), null);
    response
        .getMeta()
        .addProfile(
            "http://hl7.org/fhir/us/core/StructureDefinition/us-core-questionnaireresponse");
    response.setQuestionnaire(source.getQuestionnaire()).setStatus(source.getStatus());
    response.setAuthoredElement(source.getAuthoredElement().copy());
    response.setSubject(reference(patient));
    response.setAuthor(source.getAuthor().copy());
    response.setItem(
        source.getItem().stream()
            .map(QuestionnaireResponse.QuestionnaireResponseItemComponent::copy)
            .toList());
    return response;
  }

  private static Claim claim(
      String submission,
      DateTimeType submittedAt,
      ServiceRequest order,
      Billing billing,
      Coding service,
      String roleUrl) {
    Claim claim = profile(new Claim(), null, "profile-claim");
    claim.addIdentifier().setSystem(IDENTIFIERS + "claim").setValue(submission);
    claim.setStatus(Claim.ClaimStatus.ACTIVE).setUse(Claim.Use.PREAUTHORIZATION);
    claim.setType(concept(THO + "claim-type", "professional", null));
    claim.setCreatedElement(submittedAt.copy());
    String priority =
        switch (order.getPriority()) {
          case STAT, ASAP, URGENT -> "stat";
          default -> "normal";
        };
    claim.setPriority(concept(THO + "processpriority", priority, null));
    claim
        .addCareTeam()
        .setSequence(1)
        .setProvider(new Reference(roleUrl))
        .addExtension(PAS + "extension-careTeamClaimScope", new BooleanType(true));

    Claim.ItemComponent item =
        claim
            .addItem()
            .setSequence(1)
            .addCareTeamSequence(1)
            .setCategory(new CodeableConcept(billing.category().copy()))
            .setProductOrService(new CodeableConcept(service.copy()))
            .setLocation(concept(POS, billing.placeOfService(), null));
    item.addExtension(
        PAS + "extension-serviceItemRequestType",
        concept(X12 + "1525", "HS", "Health Services Review"));
    item.addExtension(PAS + "extension-certificationType", concept(X12 + "1322", "I", "Initial"));
    item.addExtension(PAS + "extension-requestedService", reference(order));
    for (Coding diagnosis : billing.diagnoses()) {
      int sequence = claim.getDiagnosis().size() + 1;
      claim
          .addDiagnosis()
          .setSequence(sequence)
          .setDiagnosis(new CodeableConcept(diagnosis.copy()));
      item.addDiagnosisSequence(sequence);
    }
    return claim;
  }

  private static void attach(Claim claim, QuestionnaireResponse response) {
    Extension document = new Extension(PAS + "extension-documentInformation");
    document.addExtension("reportTypeCode", concept(X12 + "755", "OZ", null));
    claim
        .addSupportingInfo()
        .setSequence(1)
        .setCategory(
            concept(
                "http://hl7.org/fhir/us/davinci-pas/CodeSystem/PASTempCodes",
                "additionalInformation",
                null))
        .setValue(reference(response))
        .addExtension(document);
  }

  private void resolveReferences(Bundle bundle, String baseUrl) {
    Map<String, String> fullUrls =
        bundle.getEntry().stream()
            .filter(entry -> entry.getResource().getIdElement().hasIdPart())
            .collect(
                Collectors.toMap(
                    entry -> reference(entry.getResource()).getReference(),
                    Bundle.BundleEntryComponent::getFullUrl));
    Set<String> urls =
        bundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getFullUrl)
            .collect(Collectors.toUnmodifiableSet());
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      for (Reference reference :
          fhirContext
              .newTerser()
              .getAllPopulatedChildElementsOfType(entry.getResource(), Reference.class)) {
        if (!reference.hasReference() || urls.contains(reference.getReference())) {
          continue;
        }
        var id = reference.getReferenceElement();
        String target = fullUrls.get(id.toUnqualifiedVersionless().getValue());
        if (target == null || (id.hasBaseUrl() && !baseUrl.equals(id.getBaseUrl()))) {
          throw new PasRequestException(
              "The PAS request references a resource outside the selected order");
        }
        reference.setReference(target);
      }
    }
  }

  private static <T extends Resource> T profile(T resource, String id, String profile) {
    if (id != null) {
      resource.setId(id);
    }
    if (profile != null) {
      resource.getMeta().addProfile(PAS + profile);
    }
    return resource;
  }

  private static Reference reference(Resource resource) {
    return new Reference(resource.fhirType() + "/" + resource.getIdPart());
  }

  private static void addEntry(Bundle bundle, String baseUrl, Resource resource) {
    bundle
        .addEntry()
        .setFullUrl(baseUrl + "/" + reference(resource).getReference())
        .setResource(resource);
  }

  private static String generatedId(String baseUrl, String type, String key) {
    return UUID.nameUUIDFromBytes(
            (baseUrl + "/" + type + "/" + key).getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  private static CodeableConcept concept(String system, String code, String display) {
    return new CodeableConcept(new Coding(system, code, display));
  }
}
