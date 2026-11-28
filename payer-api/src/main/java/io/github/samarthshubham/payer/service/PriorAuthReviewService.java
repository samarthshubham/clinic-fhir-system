package io.github.samarthshubham.payer.service;

import static io.github.samarthshubham.payer.service.ClaimSubmitService.PRIOR_AUTH_SYSTEM;
import static io.github.samarthshubham.payer.service.ClaimSubmitService.STAFF_DECISION_TAG;
import static io.github.samarthshubham.payer.service.ClaimSubmitService.applyReviewAction;
import static io.github.samarthshubham.payer.service.ClaimSubmitService.referenceOf;
import static io.github.samarthshubham.payer.service.ClaimSubmitService.utc;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import io.github.samarthshubham.payer.dto.PriorAuthDecisionRequest;
import io.github.samarthshubham.payer.dto.PriorAuthReviewResponse;
import io.github.samarthshubham.payer.exception.PriorAuthConflictException;
import io.github.samarthshubham.payer.exception.ResourceNotFoundException;
import io.github.samarthshubham.payer.mapper.PriorAuthReviewMapper;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.ClaimResponse.AdjudicationComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Service;

@Service
public class PriorAuthReviewService {

  private static final String AUTO_VERSION_REFERENCE =
      "http://hapifhir.io/fhir/StructureDefinition/auto-version-references-at-path";

  private final IGenericClient fhirClient;
  private final FhirContext fhirContext;
  private final ClaimInquiryService inquiry;
  private final PriorAuthReviewMapper mapper;
  private final PriorAuthAdjudicator adjudicator;
  private final SlaClock clock;

  public PriorAuthReviewService(
      IGenericClient fhirClient,
      FhirContext fhirContext,
      ClaimInquiryService inquiry,
      PriorAuthReviewMapper mapper,
      PriorAuthAdjudicator adjudicator,
      SlaClock clock) {
    this.fhirClient = fhirClient;
    this.fhirContext = fhirContext;
    this.inquiry = inquiry;
    this.mapper = mapper;
    this.adjudicator = adjudicator;
    this.clock = clock;
  }

  public PriorAuthReviewResponse review(String claimId) {
    Review review = loadReview(claimId);
    return toResponse(review.claim(), review.response());
  }

  public PriorAuthReviewResponse decide(
      String claimId, PriorAuthDecisionRequest request, String reviewer) {
    Review review = loadReview(claimId);
    ClaimResponse updated = review.response().copy();
    List<AdjudicationComponent> pending = pendingActions(updated);
    String version = updated.getMeta().getVersionId();
    if (pending.isEmpty() || version == null) {
      throw new PriorAuthConflictException();
    }

    PriorAuthDecision decision =
        switch (request.decision()) {
          case APPROVE -> PriorAuthDecision.of(PriorAuthDecision.Outcome.APPROVED);
          case DENY ->
              PriorAuthDecision.denied(
                  new ApprovalCriterion.DenialReason(
                      request.reasonCode(), request.reasonText().strip()));
          case PEND -> PriorAuthDecision.of(PriorAuthDecision.Outcome.PENDED);
        };
    pending.forEach(action -> applyReviewAction(action, updated.getPreAuthRef(), decision));

    Instant now = clock.now();
    updated.setCreatedElement(utc(now));
    updated.setDisposition(disposition(updated));
    if (updated.getMeta().getTag(PRIOR_AUTH_SYSTEM, STAFF_DECISION_TAG) == null) {
      updated.getMeta().addTag(PRIOR_AUTH_SYSTEM, STAFF_DECISION_TAG, null);
    }
    PriorAuthReviewResponse result = toResponse(review.claim(), updated);
    store(updated, provenance(updated, request, reviewer, now), version);
    return result;
  }

  private Review loadReview(String claimId) {
    Claim claim = read(Claim.class, claimId);
    if (claim == null
        || claim.getUse() != Claim.Use.PREAUTHORIZATION
        || claim.getStatus() != Claim.ClaimStatus.ACTIVE
        || claim.getPatient().getReferenceElement().isAbsolute()
        || !"Patient".equals(claim.getPatient().getReferenceElement().getResourceType())) {
      throw new ResourceNotFoundException("Prior authorization", claimId);
    }
    ClaimResponse response =
        inquiry
            .currentDecision(claim)
            .filter(candidate -> candidate.getStatus() == ClaimResponse.ClaimResponseStatus.ACTIVE)
            .orElseThrow(() -> new ResourceNotFoundException("Prior authorization", claimId));
    return new Review(claim, response);
  }

  private PriorAuthReviewResponse toResponse(Claim claim, ClaimResponse response) {
    List<QuestionnaireResponse> questionnaires =
        evidence(claim, QuestionnaireResponse.class)
            .filter(questionnaire -> samePatient(questionnaire.getSubject(), claim))
            .toList();
    List<DocumentReference> documents =
        evidence(claim, DocumentReference.class)
            .filter(document -> samePatient(document.getSubject(), claim))
            .toList();
    Map<String, String> ruleAnswers = new HashMap<>();
    questionnaires.forEach(
        questionnaire -> PasRequest.collectAnswers(questionnaire.getItem(), ruleAnswers));
    return mapper.toResponse(
        claim,
        response,
        !pendingActions(response).isEmpty(),
        questionnaires,
        documents,
        claim.getItem().stream().map(item -> adjudicator.rule(item.getProductOrService())).toList(),
        ruleAnswers);
  }

  private static boolean samePatient(Reference subject, Claim claim) {
    return !subject.isEmpty() && ClaimSubmitService.sameReference(subject, claim.getPatient());
  }

  private <T extends DomainResource> Stream<T> evidence(Claim claim, Class<T> type) {
    return claim.getSupportingInfo().stream()
        .map(Claim.SupportingInformationComponent::getValue)
        .filter(Reference.class::isInstance)
        .map(Reference.class::cast)
        .map(Reference::getReferenceElement)
        .filter(
            reference ->
                !reference.isAbsolute() && type.getSimpleName().equals(reference.getResourceType()))
        .filter(reference -> reference.hasIdPart())
        .distinct()
        .map(reference -> readEvidence(type, reference))
        .filter(Objects::nonNull);
  }

  private <T extends Resource> T readEvidence(Class<T> type, IIdType reference) {
    try {
      return reference.hasVersionIdPart()
          ? fhirClient.vread(type, reference.getIdPart(), reference.getVersionIdPart())
          : read(type, reference.getIdPart());
    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException ex) {
      return null;
    }
  }

  private <T extends Resource> T read(Class<T> type, String id) {
    try {
      return fhirClient.read().resource(type).withId(id).execute();
    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException ex) {
      return null;
    }
  }

  private List<AdjudicationComponent> actions(ClaimResponse response) {
    return fhirContext
        .newTerser()
        .getAllPopulatedChildElementsOfType(response, AdjudicationComponent.class);
  }

  private List<AdjudicationComponent> pendingActions(ClaimResponse response) {
    return actions(response).stream().filter(PriorAuthQueueService::hasPendingAction).toList();
  }

  private String disposition(ClaimResponse response) {
    return actions(response).stream()
        .filter(AdjudicationComponent::hasReason)
        .map(AdjudicationComponent::getReason)
        .filter(
            reason ->
                reason.getCoding().stream()
                    .anyMatch(
                        coding ->
                            ClaimSubmitService.DENIAL_REASON_SYSTEM.equals(coding.getSystem())))
        .map(reason -> "Denied: " + reason.getText())
        .findFirst()
        .orElseGet(
            () -> pendingActions(response).isEmpty() ? "Approved" : "Pended for clinical review");
  }

  private static Provenance provenance(
      ClaimResponse response, PriorAuthDecisionRequest request, String reviewer, Instant at) {
    Provenance provenance = new Provenance();
    provenance.getMeta().addProfile("http://hl7.org/fhir/StructureDefinition/Provenance");
    provenance.getMeta().addExtension(AUTO_VERSION_REFERENCE, new StringType("target"));
    provenance.addTarget(new Reference(referenceOf(response)));
    provenance.setRecorded(Date.from(at));
    provenance.setOccurred(utc(at));
    provenance.setActivity(
        new CodeableConcept()
            .addCoding(
                new Coding(
                    "http://terminology.hl7.org/CodeSystem/v3-DataOperation", "UPDATE", null)));
    provenance
        .addAgent()
        .setWho(
            new Reference()
                .setIdentifier(
                    new Identifier()
                        .setSystem(
                            QuestionnairePackageService.PAYER_BASE + "/identifier/staff-username")
                        .setValue(reviewer)));
    provenance.addReason(
        request.decision() == PriorAuthDecisionRequest.Decision.DENY
            ? new CodeableConcept(
                    new Coding(ClaimSubmitService.DENIAL_REASON_SYSTEM, request.reasonCode(), null))
                .setText(request.reasonText().strip())
            : new CodeableConcept()
                .setText(
                    request.decision() == PriorAuthDecisionRequest.Decision.APPROVE
                        ? "Approved after clinical review"
                        : "Pended for clinical review"));
    return provenance;
  }

  private void store(ClaimResponse response, Provenance provenance, String version) {
    Bundle transaction = new Bundle().setType(Bundle.BundleType.TRANSACTION);
    String resourcePath = referenceOf(response);
    transaction
        .addEntry()
        .setFullUrl(fhirClient.getServerBase() + "/" + resourcePath)
        .setResource(response)
        .getRequest()
        .setMethod(Bundle.HTTPVerb.PUT)
        .setUrl(resourcePath)
        .setIfMatch("W/\"" + version + "\"");
    transaction
        .addEntry()
        .setFullUrl("urn:uuid:" + UUID.randomUUID())
        .setResource(provenance)
        .getRequest()
        .setMethod(Bundle.HTTPVerb.POST)
        .setUrl("Provenance");
    try {
      fhirClient.transaction().withBundle(transaction).execute();
    } catch (BaseServerResponseException ex) {
      if (ex.getStatusCode() == 412 || ex.getStatusCode() == 409) {
        throw new PriorAuthConflictException();
      }
      throw ex;
    }
  }

  private record Review(Claim claim, ClaimResponse response) {}
}
