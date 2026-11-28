package io.github.samarthshubham.clinic.service.pas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import io.github.samarthshubham.clinic.client.PasInquiryClient;
import io.github.samarthshubham.clinic.client.PayerApiException;
import io.github.samarthshubham.clinic.dto.ClinicianAlert;
import io.github.samarthshubham.clinic.dto.PriorAuthResponse;
import io.github.samarthshubham.clinic.dto.PriorAuthResponse.Status;
import io.github.samarthshubham.clinic.exception.PasSubmissionUnavailableException;
import io.github.samarthshubham.clinic.service.Notifier;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import io.github.samarthshubham.clinic.support.GoldenFhir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PasDecisionServiceTest {

  private static final FhirContext FHIR = FhirContext.forR4Cached();
  private final FhirClientStub fhir = new FhirClientStub();
  private final PasInquiryClient client = mock(PasInquiryClient.class);
  private final Notifier notifier = mock(Notifier.class);
  private final PasDecisionService service =
      new PasDecisionService(fhir.client(), client, List.of(notifier), "secret");

  @Test
  void validatesWebhookSecretWithNoDefaultForAnUnconfiguredDeployment() {
    assertThat(service.isTrustedWebhookSecret("secret")).isTrue();
    assertThat(service.isTrustedWebhookSecret("wrong")).isFalse();
    assertThat(service.isTrustedWebhookSecret(null)).isFalse();
    assertThat(
            new PasDecisionService(fhir.client(), client, List.of(), "").isTrustedWebhookSecret(""))
        .isFalse();
  }

  @Test
  void keepsOnlyTheResourcesNeededToQueryTheOriginalAuthorization() throws IOException {
    Bundle stored = trackPended();
    Bundle inquiry = (Bundle) stored.getEntryFirstRep().getResource();
    Claim claim = PasInquiryRequest.claim(inquiry);

    assertThat(stored.getIdentifier().getSystem()).isEqualTo(PasDecisionService.TRACKING_SYSTEM);
    assertThat(fhir.conditionalCreates())
        .containsExactly(
            "Bundle?identifier="
                + PasDecisionService.TRACKING_SYSTEM
                + "|"
                + stored.getIdentifier().getValue());
    assertThat(inquiry.getEntry())
        .extracting(entry -> entry.getResource().fhirType())
        .containsExactly("Claim", "Organization", "Organization", "Patient", "Coverage");
    assertThat(claim.getMeta().getProfile().getFirst().getValue())
        .isEqualTo(PasInquiryRequest.PAS + "profile-claim-inquiry");
    assertThat(claim.getRelatedFirstRep().getReference().getValue()).startsWith("PX-PA-");
    assertThat(claim.getIdentifierFirstRep().getValue())
        .isNotEqualTo(claim.getRelatedFirstRep().getReference().getValue());
    assertThat(inquiry.getIdentifier().getValue())
        .isEqualTo(claim.getIdentifierFirstRep().getValue());
    assertThat(claim.hasItem()).isFalse();
    assertThat(claim.hasSupportingInfo()).isFalse();
    Path output = Path.of("target/fhir-out/pas/clinic-inquiry-request.json");
    Files.createDirectories(output.getParent());
    Files.writeString(
        output, FHIR.newJsonParser().setPrettyPrint(true).encodeResourceToString(inquiry) + "\n");
  }

  @Test
  void rejectsAnInitialResponseForAnotherPatient() {
    var initial = response("pas/response-pended.json");
    initial.setPatient(new Reference("Patient/different"));

    assertThatThrownBy(() -> service.track(bundle("pas/request-polysomnography.json"), initial))
        .isInstanceOf(PasSubmissionUnavailableException.class);
    assertThat(fhir.created()).isEmpty();
  }

  @Test
  void appliesFinalPayerDecisionAndNotifiesExactlyOnce() {
    Bundle tracked = trackPended();
    when(client.inquire(any(Bundle.class))).thenReturn(Optional.of(approval()));
    fhir.givenSearchResult(Bundle.class, searchPage(tracked));

    service.refreshPending();
    fhir.givenSearchResult(Bundle.class, searchPage((Bundle) fhir.updated().getFirst()));
    service.refreshPending();

    assertThat(fhir.updated())
        .singleElement()
        .isInstanceOfSatisfying(
            Bundle.class,
            record -> {
              var saved = (ClaimResponse) record.getEntry().get(1).getResource();
              assertThat(saved.getPreAuthRef()).isEqualTo("PHP-PA-A6041E2F");
              assertThat(saved.getDisposition()).isEqualTo("Approved");
            });
    var capture = ArgumentCaptor.forClass(Bundle.class);
    verify(client).inquire(capture.capture());
    assertThat(PasInquiryRequest.claim(capture.getValue()).getIdentifierFirstRep().getValue())
        .isNotEqualTo(
            PasInquiryRequest.claim((Bundle) tracked.getEntryFirstRep().getResource())
                .getIdentifierFirstRep()
                .getValue());
    verify(notifier)
        .send(
            new ClinicianAlert(
                "Patient/gerald-horton",
                "Bundle/tracking-1",
                "Prior authorization approved",
                ClinicianAlert.Kind.PRIOR_AUTH_DECISION));
  }

  @Test
  void keepsTheRecordPendingWhenADecisionIsAbsentPendedStaleOrMismatched() {
    var wrongPatient =
        approval().setPatient(new Reference("https://wrong.test/fhir/Patient/gerald-horton"));
    var wrongAuthorization = approval().setPreAuthRef("wrong-reference");
    var stale = approval();
    stale.getCreatedElement().setValueAsString("2020-01-01");
    when(client.inquire(any(Bundle.class)))
        .thenReturn(
            Optional.empty(),
            Optional.of(response("pas/response-pended.json")),
            Optional.of(wrongPatient),
            Optional.of(wrongAuthorization),
            Optional.of(stale));
    fhir.givenSearchResult(Bundle.class, searchPage(trackPended()));

    for (int attempt = 0; attempt < 5; attempt++) {
      service.refreshPending();
    }

    verify(client, times(5)).inquire(any(Bundle.class));
    verifyNoInteractions(notifier);
    assertThat(fhir.updated()).isEmpty();
  }

  @Test
  void matchesAnAbsoluteReplyAfterHapiMakesStoredReferencesRelative() {
    Bundle tracked = trackPended();
    Claim claim = PasInquiryRequest.claim((Bundle) tracked.getEntryFirstRep().getResource());
    ClaimResponse decision =
        approval()
            .setPatient(claim.getPatient().copy())
            .setRequestor(claim.getProvider().copy())
            .setInsurer(claim.getInsurer().copy());
    ClaimResponse storedResponse = (ClaimResponse) tracked.getEntry().get(1).getResource();
    List.of(
            claim.getPatient(),
            claim.getProvider(),
            claim.getInsurer(),
            storedResponse.getPatient(),
            storedResponse.getRequestor(),
            storedResponse.getInsurer())
        .forEach(
            reference ->
                reference.setReference(
                    reference.getReferenceElement().toUnqualifiedVersionless().getValue()));
    fhir.givenSearchResult(Bundle.class, searchPage(tracked));
    when(client.inquire(any(Bundle.class))).thenReturn(Optional.of(decision));

    service.refreshPending();

    assertThat(fhir.updated()).hasSize(1);
    verify(notifier).send(any(ClinicianAlert.class));
  }

  @Test
  void aLaterPollRecoversFromInquiryAndNotificationFailures() {
    fhir.givenSearchResult(Bundle.class, searchPage(trackPended()));
    when(client.inquire(any(Bundle.class)))
        .thenThrow(new PayerApiException("offline"))
        .thenReturn(Optional.of(approval()));
    doThrow(new IllegalStateException("Inbox unavailable"))
        .doNothing()
        .when(notifier)
        .send(any(ClinicianAlert.class));

    service.refreshPending();
    service.refreshPending();
    assertThat(fhir.updated()).isEmpty();

    service.refreshPending();
    assertThat(fhir.updated()).hasSize(1);
    verify(notifier, times(2)).send(any(ClinicianAlert.class));
  }

  @Test
  void listsOnlyMatchingPatientsAndUsesTheCurrentDecision() {
    Bundle tracked = trackPended();
    fhir.givenSearchResult(Bundle.class, searchPage(tracked));
    var expected = new PriorAuthResponse("0001", Status.PENDED, null, null, null);
    assertThat(service.listByPatient("gerald-horton")).containsExactly(expected);
    fhir.givenSearchResult(Bundle.class, searchPage(tracked));
    assertThat(service.listByPatient("other-patient")).isEmpty();
    fhir.givenSearchResult(Bundle.class, searchPage(tracked));
    assertThat(service.findByOrder(expected.orderId())).contains(expected);
  }

  @Test
  void savesAnInitialFinalDecisionAndRaisesItsInboxEntryOnce() {
    PriorAuthResponse result =
        service.track(bundle("pas/request-polysomnography.json"), approval());
    assertThat(result.status()).isEqualTo(Status.APPROVED);
    assertThat(result.authorizationNumber()).isEqualTo("PHP-PA-A6041E2F");
    Bundle stored = (Bundle) fhir.created().getFirst();
    stored.setId("tracking-1");
    fhir.givenSearchResult(Bundle.class, searchPage(stored));
    service.refreshPending();
    fhir.givenSearchResult(Bundle.class, searchPage((Bundle) fhir.updated().getFirst()));
    service.refreshPending();
    verifyNoInteractions(client);
    verify(notifier).send(any(ClinicianAlert.class));
  }

  @Test
  void reportsSubmittedForAnAcknowledgmentWithoutAReviewActionOrAuthorizationNumber() {
    ClaimResponse receipt = response("pas/response-pended.json");
    receipt.getItem().clear();
    receipt.setPreAuthRef(null);
    PriorAuthResponse result = service.track(bundle("pas/request-polysomnography.json"), receipt);
    assertThat(result.status()).isEqualTo(Status.SUBMITTED);
    assertThat(result.authorizationNumber()).isNull();
  }

  private Bundle trackPended() {
    service.track(bundle("pas/request-polysomnography.json"), response("pas/response-pended.json"));
    Bundle tracking = (Bundle) fhir.created().getFirst();
    tracking.setId("tracking-1");
    return tracking;
  }

  private static Bundle searchPage(Bundle tracking) {
    Bundle result = new Bundle().setType(Bundle.BundleType.SEARCHSET);
    result.addEntry().setResource(tracking);
    return result;
  }

  private static ClaimResponse approval() {
    Parameters answer =
        FHIR.newJsonParser()
            .parseResource(Parameters.class, GoldenFhir.read("pas/inquire-response.json"));
    return (ClaimResponse)
        ((Bundle) answer.getParameterFirstRep().getResource()).getEntryFirstRep().getResource();
  }

  private static ClaimResponse response(String fixture) {
    return (ClaimResponse) bundle(fixture).getEntryFirstRep().getResource();
  }

  private static Bundle bundle(String fixture) {
    return FHIR.newJsonParser().parseResource(Bundle.class, GoldenFhir.read(fixture));
  }
}
