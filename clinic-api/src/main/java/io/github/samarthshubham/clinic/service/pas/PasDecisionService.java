package io.github.samarthshubham.clinic.service.pas;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import io.github.samarthshubham.clinic.client.PasInquiryClient;
import io.github.samarthshubham.clinic.dto.ClinicianAlert;
import io.github.samarthshubham.clinic.dto.PriorAuthResponse;
import io.github.samarthshubham.clinic.dto.PriorAuthResponse.Status;
import io.github.samarthshubham.clinic.exception.PasSubmissionUnavailableException;
import io.github.samarthshubham.clinic.service.Notifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PasDecisionService {

  public static final String SECRET_HEADER = "X-PAS-Decision-Secret";
  static final String TRACKING_SYSTEM = "http://praxis.clinic/fhir/identifier/pas-decision";
  private static final String NOTIFIED_TAG = "inbox-notified";
  private static final String REVIEW_ACTION = PasInquiryRequest.PAS + "extension-reviewAction";
  private static final String REVIEW_CODE = PasInquiryRequest.PAS + "extension-reviewActionCode";
  private static final String X12_ACTION_SYSTEM = "https://codesystem.x12.org/005010/306";

  private final IGenericClient fhirClient;
  private final PasInquiryClient inquiryClient;
  private final List<Notifier> notifiers;
  private final String webhookSecret;

  public PasDecisionService(
      IGenericClient fhirClient,
      PasInquiryClient inquiryClient,
      List<Notifier> notifiers,
      @Value("${clinic.payer.decision-webhook-secret}") String webhookSecret) {
    this.fhirClient = fhirClient;
    this.inquiryClient = inquiryClient;
    this.notifiers = List.copyOf(notifiers);
    this.webhookSecret = webhookSecret;
  }

  public boolean isTrustedWebhookSecret(String presentedSecret) {
    return !webhookSecret.isBlank()
        && presentedSecret != null
        && MessageDigest.isEqual(
            webhookSecret.getBytes(StandardCharsets.UTF_8),
            presentedSecret.getBytes(StandardCharsets.UTF_8));
  }

  public PriorAuthResponse track(Bundle submission, ClaimResponse initialDecision) {
    Bundle request = PasInquiryRequest.fromSubmission(submission);
    var claim = PasInquiryRequest.claim(request);
    if (!matchesParties(request, initialDecision)) {
      throw new PasSubmissionUnavailableException(
          "The payer response did not match the submitted prior authorization.");
    }
    String originalId = claim.getRelatedFirstRep().getReference().getValue();
    String key = trackingKey(originalId);
    Bundle tracking =
        new Bundle()
            .setType(Bundle.BundleType.COLLECTION)
            .setIdentifier(new Identifier().setSystem(TRACKING_SYSTEM).setValue(key));
    tracking.addEntry().setFullUrl("urn:uuid:" + UUID.randomUUID()).setResource(request);
    tracking
        .addEntry()
        .setFullUrl("urn:uuid:" + UUID.randomUUID())
        .setResource(initialDecision.copy());
    var created =
        fhirClient
            .create()
            .resource(tracking)
            .conditionalByUrl("Bundle?identifier=" + TRACKING_SYSTEM + "|" + key)
            .execute();
    if (Boolean.FALSE.equals(created.getCreated())) {
      Bundle existing =
          fhirClient.read().resource(Bundle.class).withId(created.getId().getIdPart()).execute();
      return tracked(existing)
          .map(Tracking::response)
          .orElseThrow(
              () ->
                  new PasSubmissionUnavailableException(
                      "The prior authorization could not be read. Try again."));
    }
    return new Tracking(request, initialDecision).response();
  }

  Optional<PriorAuthResponse> findByOrder(String orderId) {
    String key = trackingKey(PasBundleBuilder.CLAIM_ID_PREFIX + orderId);
    Bundle found =
        search(new TokenClientParam("identifier").exactly().systemAndCode(TRACKING_SYSTEM, key));
    return found.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(Bundle.class::isInstance)
        .map(Bundle.class::cast)
        .flatMap(tracking -> tracked(tracking).stream())
        .map(Tracking::response)
        .filter(result -> orderId.equals(result.orderId()))
        .findFirst();
  }

  List<PriorAuthResponse> listByPatient(String patientId) {
    List<PriorAuthResponse> results = new ArrayList<>();
    for (Bundle page = allTracking(); ; page = fhirClient.loadPage().next(page).execute()) {
      for (var entry : page.getEntry()) {
        if (entry.getResource() instanceof Bundle bundle) {
          tracked(bundle)
              .filter(tracking -> ("Patient/" + patientId).equals(tracking.patientReference()))
              .map(Tracking::response)
              .ifPresent(results::add);
        }
      }
      if (page.getLink(Bundle.LINK_NEXT) == null) {
        return List.copyOf(results);
      }
    }
  }

  private static String trackingKey(String claimIdentifier) {
    return UUID.nameUUIDFromBytes(claimIdentifier.getBytes(StandardCharsets.UTF_8)).toString();
  }

  private Bundle allTracking() {
    return search(new TokenClientParam("identifier").hasSystemWithAnyCode(TRACKING_SYSTEM));
  }

  private Bundle search(ca.uhn.fhir.rest.gclient.ICriterion<TokenClientParam> criterion) {
    return fhirClient
        .search()
        .forResource(Bundle.class)
        .where(criterion)
        .returnBundle(Bundle.class)
        .execute();
  }

  @Scheduled(
      fixedDelayString = "${clinic.payer.decision-poll-interval}",
      initialDelayString = "${clinic.payer.decision-poll-interval}")
  public synchronized void refreshPending() {
    for (Bundle page = allTracking(); ; page = fhirClient.loadPage().next(page).execute()) {
      for (Bundle.BundleEntryComponent entry : page.getEntry()) {
        if (entry.getResource() instanceof Bundle bundle) {
          try {
            tracked(bundle).ifPresent(tracking -> refresh(bundle, tracking));
          } catch (RuntimeException e) {
            log.warn("Clinic PAS decision refresh failed: {}", e.getClass().getSimpleName());
          }
        }
      }
      if (page.getLink(Bundle.LINK_NEXT) == null) {
        return;
      }
    }
  }

  private void refresh(Bundle bundle, Tracking tracking) {
    Status status = tracking.response().status();
    if (status != Status.SUBMITTED && status != Status.PENDED) {
      if (status != Status.NOT_REQUIRED
          && bundle.getMeta().getTag(TRACKING_SYSTEM, NOTIFIED_TAG) == null) {
        recordFinal(bundle, tracking);
      }
      return;
    }
    Bundle query = PasInquiryRequest.forInquiry(tracking.inquiry(), Instant.now());
    ClaimResponse current = tracking.decision();
    inquiryClient
        .inquire(query)
        .ifPresent(
            decision -> {
              if (!matchesParties(query, decision)
                  || (current.hasPreAuthRef()
                      && !Objects.equals(current.getPreAuthRef(), decision.getPreAuthRef()))
                  || decision.getOutcome() != ClaimResponse.RemittanceOutcome.COMPLETE
                  || current.getCreated().after(decision.getCreated())) {
                return;
              }
              Tracking latest = new Tracking(tracking.inquiry(), decision);
              Status finalStatus = latest.response().status();
              if (finalStatus == Status.NOT_REQUIRED) {
                bundle.getEntry().get(1).setResource(decision);
                fhirClient.update().resource(bundle).execute();
              } else if (finalStatus != Status.SUBMITTED && finalStatus != Status.PENDED) {
                recordFinal(bundle, latest);
              }
            });
  }

  private void recordFinal(Bundle bundle, Tracking tracking) {
    String outcome =
        switch (tracking.response().status()) {
          case APPROVED -> "approved";
          case DENIED -> "denied";
          default -> "decision updated";
        };
    var alert =
        new ClinicianAlert(
            tracking.patientReference(),
            "Bundle/" + bundle.getIdPart(),
            "Prior authorization " + outcome,
            ClinicianAlert.Kind.PRIOR_AUTH_DECISION);
    // Conditional Flag creation also deduplicates a retry after acknowledgment.
    notifiers.forEach(notifier -> notifier.send(alert));
    bundle.getEntry().get(1).setResource(tracking.decision());
    bundle.getMeta().addTag(TRACKING_SYSTEM, NOTIFIED_TAG, null);
    fhirClient.update().resource(bundle).execute();
  }

  private static Optional<Tracking> tracked(Bundle bundle) {
    if (bundle.getEntry().size() != 2
        || !TRACKING_SYSTEM.equals(bundle.getIdentifier().getSystem())
        || !(bundle.getEntryFirstRep().getResource() instanceof Bundle inquiry)
        || !inquiry.hasEntry()
        || !(inquiry.getEntryFirstRep().getResource() instanceof org.hl7.fhir.r4.model.Claim claim)
        || !claim.getRelatedFirstRep().getReference().hasValue()
        || !claim
            .getRelatedFirstRep()
            .getReference()
            .getValue()
            .startsWith(PasBundleBuilder.CLAIM_ID_PREFIX)
        || !(bundle.getEntry().get(1).getResource() instanceof ClaimResponse decision)
        || !matchesParties(inquiry, decision)) {
      return Optional.empty();
    }
    return Optional.of(new Tracking(inquiry, decision));
  }

  private record Tracking(Bundle inquiry, ClaimResponse decision) {

    String patientReference() {
      return PasInquiryRequest.claim(inquiry)
          .getPatient()
          .getReferenceElement()
          .toUnqualifiedVersionless()
          .getValue();
    }

    PriorAuthResponse response() {
      String claimId =
          PasInquiryRequest.claim(inquiry).getRelatedFirstRep().getReference().getValue();
      String orderId = claimId.substring(PasBundleBuilder.CLAIM_ID_PREFIX.length());
      List<ClaimResponse.AdjudicationComponent> items =
          decision.getItem().stream().flatMap(item -> item.getAdjudication().stream()).toList();
      List<String> codes =
          items.stream().map(PasDecisionService::reviewCode).filter(Objects::nonNull).toList();
      Status status = status(codes);
      String auth =
          items.stream()
              .filter(item -> "A1".equals(reviewCode(item)))
              .map(PasDecisionService::authorizationNumber)
              .filter(Objects::nonNull)
              .findFirst()
              .orElse(codes.contains("A1") ? decision.getPreAuthRef() : null);
      CodeableConcept reason =
          items.stream()
              .filter(item -> "A3".equals(reviewCode(item)))
              .filter(ClaimResponse.AdjudicationComponent::hasReason)
              .map(ClaimResponse.AdjudicationComponent::getReason)
              .findFirst()
              .orElse(null);
      String reasonCode = reason == null ? null : reason.getCodingFirstRep().getCode();
      String reasonText = reason == null ? null : reason.getText();
      return new PriorAuthResponse(orderId, status, auth, reasonCode, reasonText);
    }

    private Status status(List<String> codes) {
      if (codes.contains("A4")) {
        return Status.PENDED;
      }
      if (decision.getOutcome() != ClaimResponse.RemittanceOutcome.COMPLETE
          || codes.isEmpty()
          || !List.of("A1", "A3", "NA").containsAll(codes)) {
        return Status.SUBMITTED;
      }
      if (codes.stream().distinct().count() > 1) {
        return Status.MIXED;
      }
      return switch (codes.getFirst()) {
        case "A1" -> Status.APPROVED;
        case "A3" -> Status.DENIED;
        default -> Status.NOT_REQUIRED;
      };
    }
  }

  private static boolean matchesParties(Bundle inquiry, ClaimResponse decision) {
    var claim = PasInquiryRequest.claim(inquiry);
    return decision.getUse() == ClaimResponse.Use.PREAUTHORIZATION
        && decision.hasCreated()
        && PasInquiryRequest.matches(claim.getPatient(), decision.getPatient())
        && PasInquiryRequest.matches(claim.getProvider(), decision.getRequestor())
        && PasInquiryRequest.matches(claim.getInsurer(), decision.getInsurer());
  }

  private static String reviewCode(ClaimResponse.AdjudicationComponent item) {
    var action = item.getExtensionByUrl(REVIEW_ACTION);
    var code = action == null ? null : action.getExtensionByUrl(REVIEW_CODE);
    if (code == null || !(code.getValue() instanceof CodeableConcept concept)) {
      return null;
    }
    return concept.getCoding().stream()
        .filter(coding -> X12_ACTION_SYSTEM.equals(coding.getSystem()))
        .findFirst()
        .map(coding -> coding.getCode())
        .orElse(null);
  }

  private static String authorizationNumber(ClaimResponse.AdjudicationComponent item) {
    var action = item.getExtensionByUrl(REVIEW_ACTION);
    var number = action == null ? null : action.getExtensionByUrl("number");
    return number != null && number.getValue() instanceof PrimitiveType<?> value
        ? value.getValueAsString()
        : null;
  }
}
