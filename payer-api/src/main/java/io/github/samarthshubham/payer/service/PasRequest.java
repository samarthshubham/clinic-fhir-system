package io.github.samarthshubham.payer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import io.github.samarthshubham.payer.mapper.MemberMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Type;

/** A PAS request bundle: its one prior auth Claim and the entries the Claim references. */
final class PasRequest {

  static final String REQUESTED_SERVICE =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-requestedService";

  private final Bundle bundle;
  private final Bundle.BundleEntryComponent claimEntry;
  private final Map<String, Bundle.BundleEntryComponent> entriesByReference = new HashMap<>();

  private PasRequest(Bundle bundle, Bundle.BundleEntryComponent claimEntry) {
    this.bundle = bundle;
    this.claimEntry = claimEntry;
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      if (!entry.hasResource()) {
        continue;
      }
      if (entry.hasFullUrl()) {
        entriesByReference.put(entry.getFullUrl(), entry);
      }
      Resource resource = entry.getResource();
      if (resource.getIdElement().hasIdPart()) {
        entriesByReference.putIfAbsent(
            resource.fhirType() + "/" + resource.getIdElement().getIdPart(), entry);
      }
    }
  }

  static PasRequest of(Bundle bundle) {
    PasRequest request = withClaim(bundle);
    Claim claim = request.claim();
    if (!claim.hasItem()) {
      throw new InvalidRequestException("The Claim has no items");
    }
    if (request.coverage().isEmpty()) {
      throw new InvalidRequestException(
          "The Claim's focal insurance must reference a Coverage in the bundle");
    }
    return request;
  }

  static PasRequest withClaim(Bundle bundle) {
    List<Bundle.BundleEntryComponent> claims =
        bundle.getEntry().stream().filter(entry -> entry.getResource() instanceof Claim).toList();
    if (claims.size() != 1) {
      throw new InvalidRequestException("The PAS request bundle must hold exactly one Claim");
    }
    PasRequest request = new PasRequest(bundle, claims.get(0));
    Claim claim = request.claim();
    if (claim.getUse() != Claim.Use.PREAUTHORIZATION) {
      throw new InvalidRequestException("Claim.use must be preauthorization");
    }
    if (request.identifier().isEmpty()) {
      throw new InvalidRequestException("The Claim needs an identifier with a system and a value");
    }
    return request;
  }

  Optional<Identifier> providerNpi() {
    return entry(claim().getProvider())
        .map(Bundle.BundleEntryComponent::getResource)
        .flatMap(
            resource ->
                switch (resource) {
                  case Organization organization -> npi(organization.getIdentifier());
                  case PractitionerRole role ->
                      entry(role.getPractitioner())
                          .map(Bundle.BundleEntryComponent::getResource)
                          .filter(Practitioner.class::isInstance)
                          .map(Practitioner.class::cast)
                          .flatMap(practitioner -> npi(practitioner.getIdentifier()));
                  default -> Optional.empty();
                });
  }

  private static Optional<Identifier> npi(List<Identifier> identifiers) {
    return identifiers.stream()
        .filter(identifier -> "http://hl7.org/fhir/sid/us-npi".equals(identifier.getSystem()))
        .filter(Identifier::hasValue)
        .findFirst();
  }

  static Reference providerReference(Bundle.BundleEntryComponent entry, FhirContext fhirContext) {
    String fullUrl = entry.getFullUrl();
    if (fullUrl != null && (fullUrl.startsWith("http://") || fullUrl.startsWith("https://"))) {
      return new Reference(fullUrl);
    }
    Reference logical = new Reference().setType(entry.getResource().fhirType());
    fhirContext.newTerser().getValues(entry.getResource(), "identifier", Identifier.class).stream()
        .findFirst()
        .ifPresent(identifier -> logical.setIdentifier(identifier.copy()));
    return logical;
  }

  Bundle bundle() {
    return bundle;
  }

  Claim claim() {
    return (Claim) claimEntry.getResource();
  }

  Bundle.BundleEntryComponent claimEntry() {
    return claimEntry;
  }

  List<Bundle.BundleEntryComponent> entries() {
    return bundle.getEntry().stream().filter(Bundle.BundleEntryComponent::hasResource).toList();
  }

  Optional<Identifier> identifier() {
    return claim().getIdentifier().stream()
        .filter(identifier -> identifier.hasSystem() && identifier.hasValue())
        .findFirst();
  }

  Optional<Bundle.BundleEntryComponent> entry(Reference reference) {
    return reference == null || !reference.hasReference()
        ? Optional.empty()
        : Optional.ofNullable(entriesByReference.get(reference.getReference()));
  }

  Claim.InsuranceComponent focalInsurance() {
    return claim().getInsurance().stream()
        .filter(Claim.InsuranceComponent::getFocal)
        .findFirst()
        .orElse(new Claim.InsuranceComponent());
  }

  Optional<Coverage> coverage() {
    return entry(focalInsurance().getCoverage())
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(Coverage.class::isInstance)
        .map(Coverage.class::cast);
  }

  String memberId() {
    Coverage coverage = coverage().orElseThrow();
    return coverage.getIdentifier().stream()
        .filter(identifier -> MemberMapper.MEMBER_ID_SYSTEM.equals(identifier.getSystem()))
        .map(Identifier::getValue)
        .findFirst()
        .orElse(coverage.getSubscriberId());
  }

  boolean expedited() {
    return expedited(claim());
  }

  static boolean expedited(Claim claim) {
    return claim.getPriority().getCoding().stream()
        .anyMatch(
            coding ->
                "http://terminology.hl7.org/CodeSystem/processpriority".equals(coding.getSystem())
                    && "stat".equals(coding.getCode()));
  }

  /** The priority of the order behind the item (ServiceRequest, DeviceRequest, …), else routine. */
  OrderPriority orderPriority(Claim.ItemComponent item, FhirContext fhirContext) {
    Extension requested = item.getExtensionByUrl(REQUESTED_SERVICE);
    if (requested == null || !(requested.getValue() instanceof Reference reference)) {
      return OrderPriority.ROUTINE;
    }
    return entry(reference)
        .map(
            entry ->
                fhirContext
                    .newTerser()
                    .getSinglePrimitiveValueOrNull(entry.getResource(), "priority"))
        .map(OrderPriority::fromCode)
        .orElse(OrderPriority.ROUTINE);
  }

  List<Bundle.BundleEntryComponent> questionnaireResponseEntries() {
    return entries().stream()
        .filter(entry -> entry.getResource() instanceof QuestionnaireResponse)
        .toList();
  }

  List<Bundle.BundleEntryComponent> documentEntries() {
    return claim().getSupportingInfo().stream()
        .map(Claim.SupportingInformationComponent::getValue)
        .filter(Reference.class::isInstance)
        .map(Reference.class::cast)
        .flatMap(reference -> entry(reference).stream())
        .filter(entry -> entry.getResource() instanceof DocumentReference)
        .distinct()
        .toList();
  }

  /** The first answer to each linkId across the bundle's QuestionnaireResponses, as text. */
  Map<String, String> answers() {
    Map<String, String> answers = new HashMap<>();
    for (Bundle.BundleEntryComponent entry : questionnaireResponseEntries()) {
      collectAnswers(((QuestionnaireResponse) entry.getResource()).getItem(), answers);
    }
    return answers;
  }

  static void collectAnswers(
      List<QuestionnaireResponse.QuestionnaireResponseItemComponent> items,
      Map<String, String> answers) {
    for (QuestionnaireResponse.QuestionnaireResponseItemComponent item : items) {
      List<QuestionnaireResponse.QuestionnaireResponseItemComponent> nested =
          new ArrayList<>(item.getItem());
      for (QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent answer :
          item.getAnswer()) {
        String text = answerText(answer.getValue());
        if (text != null) {
          answers.putIfAbsent(item.getLinkId(), text);
        }
        nested.addAll(answer.getItem());
      }
      collectAnswers(nested, answers);
    }
  }

  private static String answerText(Type value) {
    if (value instanceof Coding coding) {
      return coding.getCode();
    }
    if (value instanceof Quantity quantity) {
      return quantity.hasValue() ? quantity.getValue().toPlainString() : null;
    }
    return value != null && value.isPrimitive() ? value.primitiveValue() : null;
  }
}
