package io.github.samarthshubham.payer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import ca.uhn.fhir.rest.server.exceptions.ResourceVersionConflictException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import io.github.samarthshubham.payer.mapper.PageMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

@Service
public class ClaimSubmitService {

  public static final String INTERACTION = "Claim/$submit";
  public static final String PAS_REQUEST_BUNDLE =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-pas-request-bundle";
  static final String PAS_RESPONSE_BUNDLE =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-pas-response-bundle";
  static final String PAS_CLAIM_RESPONSE =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claimresponse";
  static final String REVIEW_ACTION =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewAction";
  static final String REVIEW_ACTION_CODE =
      "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewActionCode";
  static final String X12_REVIEW_ACTION_SYSTEM = "https://codesystem.x12.org/005010/306";
  static final String ADJUDICATION_SYSTEM = "http://terminology.hl7.org/CodeSystem/adjudication";
  public static final String DECISION_DEADLINE =
      QuestionnairePackageService.PAYER_BASE + "/StructureDefinition/decision-deadline";
  public static final String PRIOR_AUTH_SYSTEM =
      QuestionnairePackageService.PAYER_BASE + "/identifier/prior-auth";
  public static final String QUESTIONNAIRE_RESPONSE_SYSTEM =
      QuestionnairePackageService.PAYER_BASE + "/identifier/pas-questionnaire-response";
  public static final String DENIAL_REASON_SYSTEM =
      QuestionnairePackageService.PAYER_BASE + "/CodeSystem/denial-reason";

  private final IGenericClient fhirClient;
  private final FhirContext fhirContext;
  private final MemberCoverageService memberCoverageService;
  private final PriorAuthAdjudicator adjudicator;
  private final SlaClock slaClock;
  private final AuditEventService auditEventService;
  private final Clock clock;

  public ClaimSubmitService(
      IGenericClient fhirClient,
      FhirContext fhirContext,
      MemberCoverageService memberCoverageService,
      PriorAuthAdjudicator adjudicator,
      SlaClock slaClock,
      AuditEventService auditEventService,
      Clock clock) {
    this.fhirClient = fhirClient;
    this.fhirContext = fhirContext;
    this.memberCoverageService = memberCoverageService;
    this.adjudicator = adjudicator;
    this.slaClock = slaClock;
    this.auditEventService = auditEventService;
    this.clock = clock;
  }

  public Bundle submit(Bundle bundle, String clientId) {
    List<String> payerReferences = new ArrayList<>();
    String requestId = null;
    int status = 200;
    try {
      PasRequest request = PasRequest.of(bundle);
      Identifier identifier = request.identifier().orElseThrow();
      requestId = identifier.getValue();
      requireValid(bundle);
      ClaimResponse decided = firstDecision(request, identifier, payerReferences);
      return responseBundle(request, decided);
    } catch (BaseServerResponseException e) {
      status = e.getStatusCode();
      throw e;
    } catch (RuntimeException e) {
      status = 500;
      throw e;
    } finally {
      auditEventService.record(
          new AuditEventService.InteropAudit(
              INTERACTION, requestId, clientId, status, payerReferences));
    }
  }

  private synchronized ClaimResponse firstDecision(
      PasRequest request, Identifier identifier, List<String> payerReferences) {
    Instant now = slaClock.now();
    MemberCoverageCheck check =
        memberCoverageService.check(request.memberId(), LocalDate.ofInstant(now, clock.getZone()));
    Map<String, Reference> references = payerReferences(request, check);
    Claim storedClaim = request.claim().copy();
    storedClaim.setIdElement(null);
    rewriteReferences(storedClaim, references);
    return storedResponse(identifier, storedClaim, payerReferences)
        .orElseGet(
            () ->
                adjudicateAndStore(
                    request, identifier, storedClaim, references, check, now, payerReferences));
  }

  private void requireValid(Bundle bundle) {
    Bundle profiled = bundle.copy();
    // The 8.12 validator resolves the profile from meta on a resource passed to $validate.
    if (!profiled.getMeta().hasProfile(PAS_REQUEST_BUNDLE)) {
      profiled.getMeta().addProfile(PAS_REQUEST_BUNDLE);
    }
    Parameters input = new Parameters();
    input.addParameter().setName("resource").setResource(profiled);
    OperationOutcome outcome;
    try {
      outcome =
          fhirClient
              .operation()
              .onType(Bundle.class)
              .named("$validate")
              .withParameters(input)
              .returnResourceType(OperationOutcome.class)
              .execute();
    } catch (PreconditionFailedException | UnprocessableEntityException e) {
      if (!(e.getOperationOutcome() instanceof OperationOutcome failed)) {
        throw e;
      }
      outcome = failed;
    }
    OperationOutcome errors = new OperationOutcome();
    outcome.getIssue().stream()
        .filter(
            issue ->
                issue.getSeverity() == OperationOutcome.IssueSeverity.ERROR
                    || issue.getSeverity() == OperationOutcome.IssueSeverity.FATAL)
        .forEach(issue -> errors.addIssue(issue.copy()));
    if (errors.hasIssue()) {
      throw new UnprocessableEntityException(fhirContext, errors);
    }
  }

  private Optional<ClaimResponse> storedResponse(
      Identifier identifier, Claim submitted, List<String> payerReferences) {
    Bundle found =
        fhirClient
            .search()
            .forResource(Claim.class)
            .where(
                Claim.IDENTIFIER
                    .exactly()
                    .systemAndIdentifier(identifier.getSystem(), identifier.getValue()))
            .revInclude(ClaimResponse.INCLUDE_REQUEST)
            .returnBundle(Bundle.class)
            .execute();
    List<Claim> claims = PageMapper.resources(found, Claim.class);
    if (claims.isEmpty()) {
      return Optional.empty();
    }
    if (claims.size() != 1 || found.getLink(Bundle.LINK_NEXT) != null) {
      throw replayConflict();
    }
    Claim existing = claims.getFirst();
    requireSameRequest(existing, submitted);
    ClaimResponse first =
        PageMapper.resources(found, ClaimResponse.class).stream()
            .filter(response -> refersTo(response, existing))
            .min(Comparator.comparing(ClaimResponse::getCreated))
            .orElseThrow(() -> new InternalErrorException("Prior authorization has no decision"));
    return Optional.of(recordDecision(existing, first, payerReferences));
  }

  private static void requireSameRequest(Claim existing, Claim submitted) {
    if (!sameReference(existing.getPatient(), submitted.getPatient())
        || !sameReference(existing.getProvider(), submitted.getProvider())
        || !services(existing).equals(services(submitted))) {
      throw replayConflict();
    }
  }

  private static boolean sameReference(Reference existing, Reference submitted) {
    if (existing.hasReference() || submitted.hasReference()) {
      return existing.hasReference()
          && submitted.hasReference()
          && Objects.equals(
              existing.getReferenceElement().toVersionless().getValue(),
              submitted.getReferenceElement().toVersionless().getValue());
    }
    Identifier first = existing.getIdentifier();
    Identifier second = submitted.getIdentifier();
    return first.hasValue()
        && second.hasValue()
        && Objects.equals(existing.getType(), submitted.getType())
        && Objects.equals(first.getSystem(), second.getSystem())
        && Objects.equals(first.getValue(), second.getValue());
  }

  private static List<RequestedService> services(Claim claim) {
    return claim.getItem().stream()
        .map(
            item ->
                new RequestedService(
                    item.getSequence(),
                    item.getProductOrService().getCoding().stream()
                        .filter(Coding::hasCode)
                        .map(coding -> new ServiceCode(coding.getSystem(), coding.getCode()))
                        .collect(Collectors.toSet())))
        .sorted(Comparator.comparingInt(RequestedService::sequence))
        .toList();
  }

  private record RequestedService(int sequence, Set<ServiceCode> codes) {}

  private record ServiceCode(String system, String code) {}

  private static ResourceVersionConflictException replayConflict() {
    return new ResourceVersionConflictException(
        "Claim identifier is already in use for a different request");
  }

  private static boolean refersTo(ClaimResponse response, Claim claim) {
    return response.hasRequest()
        && referenceOf(claim)
            .equals(
                response.getRequest().getReferenceElement().toUnqualifiedVersionless().getValue());
  }

  private static ClaimResponse recordDecision(
      Claim claim, ClaimResponse decision, List<String> payerReferences) {
    payerReferences.add(referenceOf(claim));
    payerReferences.add(referenceOf(decision));
    return decision;
  }

  private ClaimResponse adjudicateAndStore(
      PasRequest request,
      Identifier identifier,
      Claim storedClaim,
      Map<String, Reference> references,
      MemberCoverageCheck check,
      Instant now,
      List<String> payerReferences) {
    Claim claim = request.claim();
    Map<String, String> answers = request.answers();
    Map<Integer, PriorAuthDecision> decisions = new LinkedHashMap<>();
    for (Claim.ItemComponent item : claim.getItem()) {
      decisions.put(
          item.getSequence(),
          adjudicator.adjudicate(
              item.getProductOrService(),
              request.orderPriority(item, fhirContext),
              check,
              answers));
    }
    String preAuthRef = preAuthRef(identifier);
    String claimUrl = "urn:uuid:" + stableUuid(identifier, "claim");

    Bundle transaction = new Bundle().setType(Bundle.BundleType.TRANSACTION);
    List<Bundle.BundleEntryComponent> answerEntries = request.questionnaireResponseEntries();
    for (int index = 0; index < answerEntries.size(); index++) {
      Bundle.BundleEntryComponent entry = answerEntries.get(index);
      QuestionnaireResponse stored = (QuestionnaireResponse) entry.getResource().copy();
      stored.setIdElement(null);
      rewriteReferences(stored, references);
      Identifier answerIdentifier =
          new Identifier()
              .setSystem(QUESTIONNAIRE_RESPONSE_SYSTEM)
              .setValue(stableUuid(identifier, "questionnaire-response-" + index).toString());
      stored.setIdentifier(answerIdentifier);
      transaction
          .addEntry()
          .setFullUrl(
              references
                  .get(entry.hasFullUrl() ? entry.getFullUrl() : referenceOf(entry.getResource()))
                  .getReference())
          .setResource(stored)
          .getRequest()
          .setMethod(Bundle.HTTPVerb.POST)
          .setUrl("QuestionnaireResponse")
          .setIfNoneExist(identifierQuery(answerIdentifier));
    }
    Instant received =
        slaClock.received(claim.hasCreated() ? claim.getCreated().toInstant() : null);
    storedClaim.addExtension(
        DECISION_DEADLINE, utc(slaClock.decisionDeadline(received, request.expedited())));
    transaction
        .addEntry()
        .setFullUrl(claimUrl)
        .setResource(storedClaim)
        .getRequest()
        .setMethod(Bundle.HTTPVerb.POST)
        .setUrl("Claim")
        .setIfNoneExist(identifierQuery(identifier));
    ClaimResponse response = claimResponse(storedClaim, claimUrl, preAuthRef, decisions, now);
    transaction
        .addEntry()
        .setFullUrl("urn:uuid:" + stableUuid(identifier, "claim-response"))
        .setResource(response)
        .getRequest()
        .setMethod(Bundle.HTTPVerb.POST)
        .setUrl("ClaimResponse")
        .setIfNoneExist("request=" + claimUrl);

    Bundle stored = fhirClient.transaction().withBundle(transaction).execute();
    List<Bundle.BundleEntryComponent> results = stored.getEntry();
    Bundle.BundleEntryComponent claimResult = results.get(results.size() - 2);
    Bundle.BundleEntryComponent responseResult = results.get(results.size() - 1);
    String claimId = location(claimResult);
    String responseId = location(responseResult);
    if (hasStatus(claimResult, 200)) {
      Claim existing =
          fhirClient.read().resource(Claim.class).withId(new IdType(claimId).getIdPart()).execute();
      requireSameRequest(existing, storedClaim);
      if (!hasStatus(responseResult, 200)) {
        throw new InternalErrorException("Prior authorization has no existing decision");
      }
      ClaimResponse decided =
          fhirClient
              .read()
              .resource(ClaimResponse.class)
              .withId(new IdType(responseId).getIdPart())
              .execute();
      if (!refersTo(decided, existing)) {
        throw new InternalErrorException("Prior authorization decision does not match the request");
      }
      return recordDecision(existing, decided, payerReferences);
    }
    storedClaim.setId(claimId);
    response.setId(responseId);
    response.setRequest(new Reference(claimId));
    return recordDecision(storedClaim, response, payerReferences);
  }

  private String identifierQuery(Identifier identifier) {
    return "identifier="
        + UriUtils.encode(
            new TokenParam(identifier.getSystem(), identifier.getValue())
                .getValueAsQueryToken(fhirContext),
            StandardCharsets.UTF_8);
  }

  private Map<String, Reference> payerReferences(PasRequest request, MemberCoverageCheck check) {
    Map<String, Reference> references = new HashMap<>();
    for (Bundle.BundleEntryComponent entry : request.entries()) {
      put(
          references,
          entry,
          entry.getResource() instanceof QuestionnaireResponse
              ? new Reference("urn:uuid:" + UUID.randomUUID())
              : providerReference(entry));
    }
    if (check.payerCoverageId() == null) {
      return references;
    }
    Coverage payerCoverage =
        fhirClient.read().resource(Coverage.class).withId(check.payerCoverageId()).execute();
    Claim claim = request.claim();
    replace(references, request, claim.getPatient(), payerCoverage.getBeneficiary());
    request
        .entry(request.focalInsurance().getCoverage())
        .ifPresent(
            entry -> put(references, entry, new Reference("Coverage/" + check.payerCoverageId())));
    replace(references, request, claim.getInsurer(), payerCoverage.getPayorFirstRep());
    return references;
  }

  /**
   * The payer does not store the provider's other resources, so references to them point at the
   * provider's server, or, when the bundle only gave a urn:uuid, at the resource's identifier.
   */
  private Reference providerReference(Bundle.BundleEntryComponent entry) {
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

  private static void put(
      Map<String, Reference> references, Bundle.BundleEntryComponent entry, Reference target) {
    if (entry.getResource().getIdElement().hasIdPart()) {
      references.put(referenceOf(entry.getResource()), target);
    }
    if (entry.hasFullUrl()) {
      references.put(entry.getFullUrl(), target);
    }
  }

  private static void replace(
      Map<String, Reference> references,
      PasRequest request,
      Reference submitted,
      Reference payerReference) {
    if (payerReference.hasReference()) {
      request
          .entry(submitted)
          .ifPresent(entry -> put(references, entry, new Reference(payerReference.getReference())));
    }
  }

  private void rewriteReferences(Resource resource, Map<String, Reference> references) {
    for (Reference reference :
        fhirContext.newTerser().getAllPopulatedChildElementsOfType(resource, Reference.class)) {
      Reference target = reference.hasReference() ? references.get(reference.getReference()) : null;
      if (target != null) {
        // A populated resource can make the encoder restore a cleared bundle-local reference.
        reference.setResource(null);
        reference.setReference(target.getReference());
        if (target.hasIdentifier()) {
          reference.setIdentifier(target.getIdentifier().copy()).setType(target.getType());
        }
      }
    }
  }

  private ClaimResponse claimResponse(
      Claim claim,
      String claimUrl,
      String preAuthRef,
      Map<Integer, PriorAuthDecision> decisions,
      Instant now) {
    ClaimResponse response = new ClaimResponse();
    response.getMeta().addProfile(PAS_CLAIM_RESPONSE);
    response.addIdentifier().setSystem(PRIOR_AUTH_SYSTEM).setValue(preAuthRef);
    response.setStatus(ClaimResponse.ClaimResponseStatus.ACTIVE);
    response.setType(claim.getType().copy());
    response.setUse(ClaimResponse.Use.PREAUTHORIZATION);
    response.setPatient(claim.getPatient().copy());
    response.setCreatedElement(utc(now));
    response.setInsurer(claim.getInsurer().copy());
    response.setRequestor(claim.getProvider().copy());
    response.setRequest(new Reference(claimUrl));
    response.setOutcome(ClaimResponse.RemittanceOutcome.COMPLETE);
    response.setDisposition(disposition(decisions.values()));
    response.setPreAuthRef(preAuthRef);
    decisions.forEach(
        (sequence, decision) -> {
          ClaimResponse.AdjudicationComponent adjudication =
              response.addItem().setItemSequence(sequence).addAdjudication();
          Extension reviewAction = adjudication.addExtension().setUrl(REVIEW_ACTION);
          reviewAction.addExtension(REVIEW_ACTION_CODE, reviewActionCode(decision.outcome()));
          if (decision.outcome() == PriorAuthDecision.Outcome.APPROVED) {
            reviewAction.addExtension("number", new StringType(preAuthRef));
          }
          adjudication.setCategory(
              new CodeableConcept(new Coding(ADJUDICATION_SYSTEM, "submitted", null)));
          if (decision.denialReason() != null) {
            adjudication.setReason(
                new CodeableConcept(
                        new Coding(DENIAL_REASON_SYSTEM, decision.denialReason().code(), null))
                    .setText(decision.denialReason().text()));
          }
        });
    return response;
  }

  private Bundle responseBundle(PasRequest request, ClaimResponse decided) {
    Claim claim = request.claim();
    ClaimResponse response = decided.copy();
    response.setMeta(new Meta().setProfile(decided.getMeta().getProfile()));
    response.setId(decided.getIdElement().getIdPart());
    response.setPatient(claim.getPatient().copy());
    response.setInsurer(claim.getInsurer().copy());
    response.setRequestor(claim.getProvider().copy());
    response.setRequest(new Reference(submittedReference(request.claimEntry())));

    Bundle bundle = new Bundle();
    bundle.getMeta().addProfile(PAS_RESPONSE_BUNDLE);
    bundle.setIdentifier(request.bundle().getIdentifier().copy());
    bundle.setType(Bundle.BundleType.COLLECTION);
    bundle.setTimestampElement(
        new InstantType(
            response.getCreated(), TemporalPrecisionEnum.SECOND, TimeZone.getTimeZone("UTC")));
    bundle
        .addEntry()
        .setFullUrl(
            QuestionnairePackageService.PAYER_BASE
                + "/ClaimResponse/"
                + response.getIdElement().getIdPart())
        .setResource(response);
    List<String> added = new ArrayList<>();
    for (Reference reference :
        List.of(response.getPatient(), response.getInsurer(), response.getRequestor())) {
      request
          .entry(reference)
          .filter(entry -> !added.contains(entry.getFullUrl()))
          .ifPresent(
              entry -> {
                added.add(entry.getFullUrl());
                bundle
                    .addEntry()
                    .setFullUrl(entry.getFullUrl())
                    .setResource(entry.getResource().copy());
              });
    }
    return bundle;
  }

  private static CodeableConcept reviewActionCode(PriorAuthDecision.Outcome outcome) {
    Coding coding =
        switch (outcome) {
          case APPROVED -> new Coding(X12_REVIEW_ACTION_SYSTEM, "A1", "Certified in total");
          case DENIED -> new Coding(X12_REVIEW_ACTION_SYSTEM, "A3", "Not Certified");
          case PENDED -> new Coding(X12_REVIEW_ACTION_SYSTEM, "A4", "Pending");
          case NOT_REQUIRED -> new Coding(X12_REVIEW_ACTION_SYSTEM, "NA", "No Action Required");
        };
    return new CodeableConcept(coding);
  }

  private static String disposition(Iterable<PriorAuthDecision> decisions) {
    String denial = null;
    boolean pended = false;
    boolean approved = false;
    for (PriorAuthDecision decision : decisions) {
      switch (decision.outcome()) {
        case DENIED -> denial = denial == null ? decision.denialReason().text() : denial;
        case PENDED -> pended = true;
        case APPROVED -> approved = true;
        case NOT_REQUIRED -> {}
      }
    }
    if (denial != null) {
      return "Denied: " + denial;
    }
    if (pended) {
      return "Pended for clinical review";
    }
    return approved ? "Approved" : "No prior authorization needed";
  }

  private static String preAuthRef(Identifier identifier) {
    return "PHP-PA-"
        + stableUuid(identifier, "prior-auth").toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }

  private static UUID stableUuid(Identifier identifier, String suffix) {
    return UUID.nameUUIDFromBytes(
        (identifier.getSystem() + "|" + identifier.getValue() + "|" + suffix)
            .getBytes(StandardCharsets.UTF_8));
  }

  private static String submittedReference(Bundle.BundleEntryComponent entry) {
    String fullUrl = entry.getFullUrl();
    if (fullUrl != null && fullUrl.startsWith("urn:")) {
      return fullUrl;
    }
    return referenceOf(entry.getResource());
  }

  private static String referenceOf(Resource resource) {
    return resource.fhirType() + "/" + resource.getIdElement().getIdPart();
  }

  private static boolean hasStatus(Bundle.BundleEntryComponent result, int status) {
    String code = Integer.toString(status);
    String actual = result.getResponse().getStatus();
    return code.equals(actual) || (actual != null && actual.startsWith(code + " "));
  }

  private static String location(Bundle.BundleEntryComponent result) {
    return new IdType(result.getResponse().getLocation()).toUnqualifiedVersionless().getValue();
  }

  private static DateTimeType utc(Instant instant) {
    return new DateTimeType(
        Date.from(instant), TemporalPrecisionEnum.SECOND, TimeZone.getTimeZone("UTC"));
  }
}
