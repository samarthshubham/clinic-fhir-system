package io.github.samarthshubham.payer.service;

import static io.github.samarthshubham.payer.service.ClaimSubmitService.DECISION_DEADLINE;
import static io.github.samarthshubham.payer.service.ClaimSubmitService.REVIEW_ACTION;
import static io.github.samarthshubham.payer.service.ClaimSubmitService.REVIEW_ACTION_CODE;
import static io.github.samarthshubham.payer.service.ClaimSubmitService.X12_REVIEW_ACTION_SYSTEM;
import static io.github.samarthshubham.payer.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.samarthshubham.payer.dto.PriorAuthQueueItem;
import io.github.samarthshubham.payer.mapper.MemberMapper;
import io.github.samarthshubham.payer.mapper.PriorAuthQueueMapper;
import io.github.samarthshubham.payer.support.FhirClientStub;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

class PriorAuthQueueServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");
  private final FhirClientStub fhir = new FhirClientStub();
  private final PriorAuthQueueService service =
      new PriorAuthQueueService(
          fhir.client(),
          new SlaClock(Clock.fixed(NOW, ZoneOffset.UTC)),
          new PriorAuthQueueMapper());

  @Test
  void mapsAStoredDeadlineAndFlagsAnOverduePendingClaim() {
    Claim claim = claim("1", "normal", "2026-11-10T10:00:00Z");
    claim.addExtension(DECISION_DEADLINE, new DateTimeType("2026-11-26T10:00:00Z"));
    fhir.givenSearchResult(Claim.class, bundleOf(claim, decision("1", NOW, "A4"), patient()));

    var queue = service.pended();

    assertThat(queue.total()).isEqualTo(1);
    assertThat(queue.items())
        .containsExactly(
            new PriorAuthQueueItem(
                "1",
                "PHP-PA-1",
                "PHP-10006",
                "Tony Stark",
                "Cardiac stress test",
                false,
                NOW.minusSeconds(86_400),
                -86_400,
                true));
    assertThat(fhir.lastSearchCriteria(Claim.class))
        .contains("use=preauthorization", "status=active", "_revinclude=ClaimResponse:request");
  }

  @Test
  void calculatesExpeditedDeadlineForLegacyClaimWithoutExtension() {
    Claim claim = claim("1", "stat", "2026-11-26T10:00:00Z");
    fhir.givenSearchResult(Claim.class, bundleOf(claim, decision("1", NOW, "A4"), patient()));

    assertThat(service.pended().items())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.expedited()).isTrue();
              assertThat(item.decisionDeadline()).isEqualTo("2026-11-29T10:00:00Z");
              assertThat(item.secondsRemaining()).isEqualTo(172_800);
              assertThat(item.breached()).isFalse();
            });
  }

  @Test
  void aFinalDecisionRemovesTheClaimEvenWhenAnOlderDecisionWasPended() {
    Claim claim = claim("1", "normal", "2026-11-26T10:00:00Z");
    ClaimResponse pending = decision("1", NOW.minusSeconds(60), "A4");
    ClaimResponse approved = decision("1", NOW, "A1");
    fhir.givenSearchResult(Claim.class, bundleOf(claim, approved, pending, patient()));

    assertThat(service.pended().items()).isEmpty();
  }

  @Test
  void decisionsForDifferentPatientsCannotPutAClaimOnTheQueue() {
    Claim claim = claim("1", "normal", "2026-11-26T10:00:00Z");
    ClaimResponse mismatched = decision("1", NOW, "A4").setPatient(new Reference("Patient/other"));
    fhir.givenSearchResult(Claim.class, bundleOf(claim, mismatched, patient()));

    assertThat(service.pended().items()).isEmpty();
  }

  private static Claim claim(String id, String priority, String created) {
    Claim claim =
        new Claim()
            .setStatus(Claim.ClaimStatus.ACTIVE)
            .setUse(Claim.Use.PREAUTHORIZATION)
            .setPatient(new Reference("Patient/tony"))
            .setPriority(
                new CodeableConcept(
                    new Coding(
                        "http://terminology.hl7.org/CodeSystem/processpriority", priority, null)))
            .setCreatedElement(new DateTimeType(created));
    claim.setId(id);
    claim.addItem().setProductOrService(new CodeableConcept().setText("Cardiac stress test"));
    return claim;
  }

  private static ClaimResponse decision(String claimId, Instant at, String code) {
    ClaimResponse response =
        new ClaimResponse()
            .setUse(ClaimResponse.Use.PREAUTHORIZATION)
            .setStatus(ClaimResponse.ClaimResponseStatus.ACTIVE)
            .setPatient(new Reference("Patient/tony"))
            .setRequest(new Reference("Claim/" + claimId))
            .setPreAuthRef("PHP-PA-" + claimId)
            .setCreated(Date.from(at))
            .setOutcome(ClaimResponse.RemittanceOutcome.COMPLETE);
    response.setId(code);
    response
        .addItem()
        .setItemSequence(1)
        .addAdjudication()
        .addExtension()
        .setUrl(REVIEW_ACTION)
        .addExtension(
            REVIEW_ACTION_CODE,
            new CodeableConcept(new Coding(X12_REVIEW_ACTION_SYSTEM, code, null)));
    return response;
  }

  private static Patient patient() {
    Patient patient = new Patient();
    patient.setId("tony");
    patient.addName().addGiven("Tony").setFamily("Stark");
    patient.addIdentifier().setSystem(MemberMapper.MEMBER_ID_SYSTEM).setValue("PHP-10006");
    return patient;
  }
}
