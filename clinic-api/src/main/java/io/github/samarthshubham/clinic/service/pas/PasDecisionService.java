package io.github.samarthshubham.clinic.service.pas;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import io.github.samarthshubham.clinic.client.PasInquiryClient;
import io.github.samarthshubham.clinic.dto.ClinicianAlert;
import io.github.samarthshubham.clinic.service.Notifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Identifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PasDecisionService {

  public static final String SECRET_HEADER = "X-PAS-Decision-Secret";
  static final String TRACKING_SYSTEM = "http://praxis.clinic/fhir/identifier/pas-decision";
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

  public void track(Bundle submission, ClaimResponse initialDecision) {
    Bundle request = PasInquiryRequest.fromSubmission(submission);
    var claim = PasInquiryRequest.claim(request);
    if (!matchesParties(request, initialDecision) || !initialDecision.hasPreAuthRef()) {
      throw new IllegalArgumentException(
          "PAS response does not identify the submitted authorization");
    }
    String originalId = claim.getRelatedFirstRep().getReference().getValue();
    String key = UUID.nameUUIDFromBytes(originalId.getBytes(StandardCharsets.UTF_8)).toString();
    Bundle tracking =
        new Bundle()
            .setType(Bundle.BundleType.COLLECTION)
            .setIdentifier(new Identifier().setSystem(TRACKING_SYSTEM).setValue(key));
    tracking.addEntry().setFullUrl("urn:uuid:" + UUID.randomUUID()).setResource(request);
    tracking
        .addEntry()
        .setFullUrl("urn:uuid:" + UUID.randomUUID())
        .setResource(initialDecision.copy());
    fhirClient
        .create()
        .resource(tracking)
        .conditionalByUrl("Bundle?identifier=" + TRACKING_SYSTEM + "|" + key)
        .execute();
  }

  @Scheduled(
      fixedDelayString = "${clinic.payer.decision-poll-interval}",
      initialDelayString = "${clinic.payer.decision-poll-interval}")
  public synchronized void refreshPending() {
    Bundle page =
        fhirClient
            .search()
            .forResource(Bundle.class)
            .where(new TokenClientParam("identifier").hasSystemWithAnyCode(TRACKING_SYSTEM))
            .returnBundle(Bundle.class)
            .execute();
    while (true) {
      for (Bundle.BundleEntryComponent entry : page.getEntry()) {
        if (entry.getResource() instanceof Bundle tracking) {
          try {
            refresh(tracking);
          } catch (RuntimeException e) {
            log.warn("Clinic PAS decision refresh failed: {}", e.getClass().getSimpleName());
          }
        }
      }
      if (page.getLink(Bundle.LINK_NEXT) == null) {
        return;
      }
      page = fhirClient.loadPage().next(page).execute();
    }
  }

  private void refresh(Bundle tracking) {
    if (tracking.getEntry().size() != 2
        || !(tracking.getEntryFirstRep().getResource() instanceof Bundle template)
        || !(tracking.getEntry().get(1).getResource() instanceof ClaimResponse current)
        || !reviewCodes(current).contains("A4")) {
      return;
    }
    Bundle query = PasInquiryRequest.forInquiry(template, Instant.now());
    inquiryClient
        .inquire(query)
        .ifPresent(
            decision -> {
              if (!matchesParties(query, decision)
                  || !Objects.equals(current.getPreAuthRef(), decision.getPreAuthRef())
                  || decision.getOutcome() != ClaimResponse.RemittanceOutcome.COMPLETE
                  || current.getCreated().after(decision.getCreated())) {
                return;
              }
              String outcome = finalOutcome(decision);
              if (outcome == null) {
                return;
              }
              var patient = PasInquiryRequest.claim(query).getPatient().getReferenceElement();
              var alert =
                  new ClinicianAlert(
                      patient.toUnqualifiedVersionless().getValue(),
                      "Bundle/" + tracking.getIdPart(),
                      "Prior authorization " + outcome,
                      ClinicianAlert.Kind.PRIOR_AUTH_DECISION);
              // Retrying a failed decision write reuses the same Inbox key, including after
              // acknowledgment.
              notifiers.forEach(notifier -> notifier.send(alert));
              tracking.getEntry().get(1).setResource(decision);
              fhirClient.update().resource(tracking).execute();
            });
  }

  private static boolean matchesParties(Bundle inquiry, ClaimResponse decision) {
    var claim = PasInquiryRequest.claim(inquiry);
    return decision.getUse() == ClaimResponse.Use.PREAUTHORIZATION
        && decision.hasCreated()
        && PasInquiryRequest.matches(claim.getPatient(), decision.getPatient())
        && PasInquiryRequest.matches(claim.getProvider(), decision.getRequestor())
        && PasInquiryRequest.matches(claim.getInsurer(), decision.getInsurer());
  }

  private static List<String> reviewCodes(ClaimResponse decision) {
    return decision.getItem().stream()
        .flatMap(item -> item.getAdjudication().stream())
        .map(adjudication -> adjudication.getExtensionByUrl(REVIEW_ACTION))
        .filter(Objects::nonNull)
        .map(action -> action.getExtensionByUrl(REVIEW_CODE))
        .filter(Objects::nonNull)
        .flatMap(
            extension ->
                extension.getValue() instanceof CodeableConcept concept
                    ? concept.getCoding().stream()
                    : java.util.stream.Stream.empty())
        .filter(coding -> X12_ACTION_SYSTEM.equals(coding.getSystem()))
        .map(coding -> coding.getCode())
        .toList();
  }

  private static String finalOutcome(ClaimResponse decision) {
    List<String> codes = reviewCodes(decision);
    if (codes.isEmpty() || !List.of("A1", "A3", "NA").containsAll(codes)) {
      return null;
    }
    return switch (codes.stream().distinct().count() == 1 ? codes.getFirst() : "mixed") {
      case "A1" -> "approved";
      case "A3" -> "denied";
      case "NA" -> "not required";
      default -> "decision updated";
    };
  }
}
