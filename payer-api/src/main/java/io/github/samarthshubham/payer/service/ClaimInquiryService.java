package io.github.samarthshubham.payer.service;

import static io.github.samarthshubham.payer.service.ClaimSubmitService.PRIOR_AUTH_SYSTEM;
import static io.github.samarthshubham.payer.service.ClaimSubmitService.referenceOf;
import static io.github.samarthshubham.payer.service.ClaimSubmitService.refersTo;
import static io.github.samarthshubham.payer.service.ClaimSubmitService.sameReference;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import io.github.samarthshubham.payer.mapper.MemberMapper;
import io.github.samarthshubham.payer.mapper.PageMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Service;

@Service
public class ClaimInquiryService {

  private static final String INTERACTION = "Claim/$inquire";
  private static final String PAS = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/";
  private static final String REQUEST_BUNDLE = PAS + "profile-pas-inquiry-request-bundle";
  private static final String RESPONSE_BUNDLE = PAS + "profile-pas-inquiry-response-bundle";
  private static final String CLAIM_RESPONSE = PAS + "profile-claiminquiryresponse";
  private static final Set<String> AUTHORIZATION_REFERENCE_EXTENSIONS =
      Set.of(
          PAS + "extension-authorizationNumber", PAS + "extension-administrationReferenceNumber");

  private final IGenericClient fhirClient;
  private final FhirContext fhirContext;
  private final AuditEventService auditEventService;
  private final Clock clock;

  public ClaimInquiryService(
      IGenericClient fhirClient,
      FhirContext fhirContext,
      AuditEventService auditEventService,
      Clock clock) {
    this.fhirClient = fhirClient;
    this.fhirContext = fhirContext;
    this.auditEventService = auditEventService;
    this.clock = clock;
  }

  public Parameters inquire(Bundle bundle, String clientId) {
    List<String> payerReferences = new ArrayList<>();
    String trace = null;
    int status = 200;
    try {
      PasRequest inquiry = PasRequest.withClaim(bundle);
      trace = inquiry.identifier().orElseThrow().getValue();
      PasBundleValidation.requireValid(fhirClient, fhirContext, bundle, REQUEST_BUNDLE);
      return findDecisions(inquiry, payerReferences);
    } catch (BaseServerResponseException e) {
      status = e.getStatusCode();
      throw e;
    } catch (RuntimeException e) {
      status = 500;
      throw e;
    } finally {
      auditEventService.record(
          new AuditEventService.InteropAudit(
              INTERACTION, trace, clientId, status, payerReferences));
    }
  }

  private Parameters findDecisions(PasRequest inquiry, List<String> payerReferences) {
    Claim request = inquiry.claim();
    Bundle.BundleEntryComponent patientEntry =
        inquiry.entry(request.getPatient()).orElseThrow(ClaimInquiryService::missingMember);
    if (!(patientEntry.getResource() instanceof Patient patient)) {
      throw missingMember();
    }
    Identifier member =
        patient.getIdentifier().stream()
            .filter(identifier -> MemberMapper.MEMBER_ID_SYSTEM.equals(identifier.getSystem()))
            .filter(Identifier::hasValue)
            .findFirst()
            .orElseThrow(ClaimInquiryService::missingMember);
    Identifier npi =
        inquiry
            .providerNpi()
            .orElseThrow(
                () ->
                    new InvalidRequestException(
                        "The inquiry needs a bundled provider with an NPI"));
    Reference provider =
        PasRequest.providerReference(
            inquiry.entry(request.getProvider()).orElseThrow(), fhirContext);
    Reference submittedPatient = PasRequest.providerReference(patientEntry, fhirContext);

    Parameters results = new Parameters();
    Set<String> handled = new LinkedHashSet<>();
    for (Claim claim : candidateClaims(request)) {
      if (claim.getUse() != Claim.Use.PREAUTHORIZATION
          || !handled.add(referenceOf(claim))
          || !matchesProvider(claim.getProvider(), provider, npi)
          || !matchesPatient(claim.getPatient(), submittedPatient, member)) {
        continue;
      }
      currentDecision(claim)
          .ifPresent(
              decision -> {
                payerReferences.add(referenceOf(claim));
                payerReferences.add(referenceOf(decision));
                results
                    .addParameter()
                    .setName("return")
                    .setResource(responseBundle(inquiry, claim, decision));
              });
    }
    return results;
  }

  private List<Claim> candidateClaims(Claim request) {
    List<Identifier> identifiers =
        request.getRelated().stream()
            .filter(Claim.RelatedClaimComponent::hasReference)
            .map(Claim.RelatedClaimComponent::getReference)
            .filter(identifier -> identifier.hasSystem() && identifier.hasValue())
            .toList();
    Set<String> authorizationNumbers = new LinkedHashSet<>();
    addAuthorizationNumbers(authorizationNumbers, request.getExtension());
    request
        .getItem()
        .forEach(item -> addAuthorizationNumbers(authorizationNumbers, item.getExtension()));
    request
        .getInsurance()
        .forEach(
            insurance ->
                insurance
                    .getPreAuthRef()
                    .forEach(reference -> authorizationNumbers.add(reference.getValue())));

    List<Claim> claims = new ArrayList<>();
    for (Identifier identifier : identifiers) {
      Bundle matches =
          fhirClient
              .search()
              .forResource(Claim.class)
              .where(
                  Claim.IDENTIFIER
                      .exactly()
                      .systemAndIdentifier(identifier.getSystem(), identifier.getValue()))
              .returnBundle(Bundle.class)
              .execute();
      resources(matches, Claim.class).stream()
          .filter(
              claim ->
                  claim.getIdentifier().stream()
                      .anyMatch(stored -> sameIdentifier(stored, identifier)))
          .forEach(claims::add);
    }
    for (String authorization : authorizationNumbers) {
      Bundle matches =
          fhirClient
              .search()
              .forResource(ClaimResponse.class)
              .where(
                  ClaimResponse.IDENTIFIER
                      .exactly()
                      .systemAndIdentifier(PRIOR_AUTH_SYSTEM, authorization))
              .include(ClaimResponse.INCLUDE_REQUEST)
              .returnBundle(Bundle.class)
              .execute();
      List<Claim> linkedClaims = PageMapper.resources(matches, Claim.class);
      List<ClaimResponse> decisions = PageMapper.resources(matches, ClaimResponse.class);
      linkedClaims.stream()
          .filter(
              claim ->
                  decisions.stream()
                      .anyMatch(
                          decision ->
                              authorization.equals(decision.getPreAuthRef())
                                  && refersTo(decision, claim)))
          .forEach(claims::add);
    }
    return claims;
  }

  private static void addAuthorizationNumbers(Set<String> numbers, List<Extension> extensions) {
    extensions.stream()
        .filter(extension -> AUTHORIZATION_REFERENCE_EXTENSIONS.contains(extension.getUrl()))
        .map(Extension::getValue)
        .filter(StringType.class::isInstance)
        .map(StringType.class::cast)
        .filter(StringType::hasValue)
        .map(StringType::getValue)
        .forEach(numbers::add);
  }

  private static boolean matchesProvider(Reference stored, Reference submitted, Identifier npi) {
    if (!sameReference(stored, submitted)) {
      return false;
    }
    return !"http://hl7.org/fhir/sid/us-npi".equals(stored.getIdentifier().getSystem())
        || sameIdentifier(stored.getIdentifier(), npi);
  }

  private boolean matchesPatient(Reference stored, Reference submitted, Identifier member) {
    Optional<String> payerPatient = localId(stored, "Patient");
    if (payerPatient.isEmpty()) {
      return sameReference(stored, submitted) && sameIdentifier(stored.getIdentifier(), member);
    }
    try {
      Patient patient =
          fhirClient.read().resource(Patient.class).withId(payerPatient.get()).execute();
      return patient.getIdentifier().stream()
          .anyMatch(identifier -> sameIdentifier(identifier, member));
    } catch (ResourceNotFoundException e) {
      return false;
    }
  }

  private Optional<ClaimResponse> currentDecision(Claim claim) {
    Bundle decisions =
        fhirClient
            .search()
            .forResource(ClaimResponse.class)
            .where(ClaimResponse.REQUEST.hasId(referenceOf(claim)))
            .returnBundle(Bundle.class)
            .execute();
    return resources(decisions, ClaimResponse.class).stream()
        .filter(response -> response.getUse() == ClaimResponse.Use.PREAUTHORIZATION)
        .filter(
            response ->
                localId(response.getRequest(), "Claim")
                    .filter(claim.getIdPart()::equals)
                    .isPresent())
        .filter(response -> sameReference(response.getPatient(), claim.getPatient()))
        .filter(ClaimResponse::hasCreated)
        .max(
            Comparator.comparing(ClaimResponse::getCreated)
                .thenComparing(
                    response -> response.getMeta().getLastUpdated(),
                    Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ClaimResponse::getIdPart));
  }

  private <T extends Resource> List<T> resources(Bundle first, Class<T> type) {
    List<T> matches = new ArrayList<>();
    Bundle page = first;
    while (true) {
      matches.addAll(PageMapper.resources(page, type));
      if (page.getLink(Bundle.LINK_NEXT) == null) {
        return matches;
      }
      page = fhirClient.loadPage().next(page).execute();
    }
  }

  private static Optional<String> localId(Reference reference, String type) {
    if (!reference.hasReference() || reference.getReferenceElement().isAbsolute()) {
      return Optional.empty();
    }
    IIdType id = reference.getReferenceElement();
    return type.equals(id.getResourceType()) && id.hasIdPart()
        ? Optional.of(id.getIdPart())
        : Optional.empty();
  }

  private static boolean sameIdentifier(Identifier first, Identifier second) {
    return first.hasValue()
        && Objects.equals(first.getSystem(), second.getSystem())
        && first.getValue().equals(second.getValue());
  }

  private Bundle responseBundle(PasRequest inquiry, Claim claim, ClaimResponse decision) {
    Claim request = inquiry.claim();
    ClaimResponse wire = decision.copy();
    wire.setMeta(new Meta().addProfile(CLAIM_RESPONSE));
    wire.setId(decision.getIdPart());
    wire.setPatient(responseReference(inquiry, request.getPatient()));
    wire.setInsurer(responseReference(inquiry, request.getInsurer()));
    wire.setRequestor(responseReference(inquiry, request.getProvider()));
    wire.setRequest(
        new Reference(QuestionnairePackageService.PAYER_BASE + "/" + referenceOf(claim)));

    Bundle bundle = new Bundle().setType(Bundle.BundleType.COLLECTION);
    bundle.getMeta().addProfile(RESPONSE_BUNDLE);
    bundle.getTimestampElement().setValueAsString(clock.instant().toString());
    bundle
        .addEntry()
        .setFullUrl(QuestionnairePackageService.PAYER_BASE + "/" + referenceOf(wire))
        .setResource(wire);
    Set<String> included = new LinkedHashSet<>();
    for (Reference reference : List.of(wire.getPatient(), wire.getInsurer(), wire.getRequestor())) {
      inquiry
          .entry(reference)
          .filter(entry -> included.add(entry.getFullUrl()))
          .ifPresent(
              entry ->
                  bundle
                      .addEntry()
                      .setFullUrl(entry.getFullUrl())
                      .setResource(entry.getResource().copy()));
    }
    return bundle;
  }

  private static Reference responseReference(PasRequest inquiry, Reference source) {
    Reference wire = source.copy();
    inquiry
        .entry(source)
        .filter(Bundle.BundleEntryComponent::hasFullUrl)
        .ifPresent(entry -> wire.setReference(entry.getFullUrl()));
    return wire;
  }

  private static InvalidRequestException missingMember() {
    return new InvalidRequestException(
        "The inquiry needs a bundled patient with a Praxis member identifier");
  }
}
