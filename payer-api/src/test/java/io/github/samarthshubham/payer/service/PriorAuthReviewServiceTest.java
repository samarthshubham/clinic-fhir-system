package io.github.samarthshubham.payer.service;

import static io.github.samarthshubham.payer.service.ClaimSubmitService.REVIEW_ACTION;
import static io.github.samarthshubham.payer.service.ClaimSubmitService.REVIEW_ACTION_CODE;
import static io.github.samarthshubham.payer.service.ClaimSubmitService.X12_REVIEW_ACTION_SYSTEM;
import static io.github.samarthshubham.payer.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import io.github.samarthshubham.payer.dto.PriorAuthDecisionRequest;
import io.github.samarthshubham.payer.dto.PriorAuthDecisionRequest.Decision;
import io.github.samarthshubham.payer.dto.PriorAuthReviewResponse;
import io.github.samarthshubham.payer.exception.PriorAuthConflictException;
import io.github.samarthshubham.payer.exception.ResourceNotFoundException;
import io.github.samarthshubham.payer.mapper.PriorAuthReviewMapper;
import io.github.samarthshubham.payer.support.FhirClientStub;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PriorAuthReviewServiceTest {

  private static final FhirContext FHIR = FhirContext.forR4Cached();
  private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");
  private static final String CPT = "http://www.ama-assn.org/go/cpt";
  private final FhirClientStub fhir = new FhirClientStub();
  private final Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
  private final CoverageRuleService rules = mock(CoverageRuleService.class);
  private final PriorAuthReviewService service =
      new PriorAuthReviewService(
          fhir.client(),
          FHIR,
          new ClaimInquiryService(fhir.client(), FHIR, mock(AuditEventService.class), fixed),
          new PriorAuthReviewMapper(),
          new PriorAuthAdjudicator(rules),
          new SlaClock(fixed));

  private Claim claim;
  private ClaimResponse response;

  @BeforeEach
  void setUp() {
    when(fhir.client().getServerBase()).thenReturn("http://localhost:8081/fhir");
    claim =
        new Claim()
            .setUse(Claim.Use.PREAUTHORIZATION)
            .setStatus(Claim.ClaimStatus.ACTIVE)
            .setPatient(new Reference("Patient/tony"));
    claim
        .addItem()
        .setSequence(1)
        .setProductOrService(new CodeableConcept(new Coding(CPT, "93015", "Cardiac stress test")));
    fhir.givenStored(claim, "claim-1");

    response =
        new ClaimResponse()
            .setUse(ClaimResponse.Use.PREAUTHORIZATION)
            .setStatus(ClaimResponse.ClaimResponseStatus.ACTIVE)
            .setCreated(Date.from(NOW.minusSeconds(60)))
            .setRequest(new Reference("Claim/claim-1"))
            .setPatient(claim.getPatient().copy())
            .setPreAuthRef("PHP-PA-1");
    response.setId("ClaimResponse/response-1");
    response.getMeta().setVersionId("4");
    addAction(1, "A4");
    addAction(2, "A1");
    fhir.givenSearchResult(ClaimResponse.class, bundleOf(response));
    when(rules.findRule(CPT, "93015"))
        .thenReturn(
            new CoverageRule(
                CPT,
                "93015",
                "Cardiac stress test",
                true,
                true,
                null,
                List.of(
                    new ApprovalCriterion(
                        "exercise", ApprovalCriterion.Operator.EQUALS, "true", null)),
                false));
  }

  @Test
  void showsClaimLinesMatchedCriteriaAndOnlyEvidenceBelongingToThePatient() {
    QuestionnaireResponse qr = new QuestionnaireResponse().setSubject(claim.getPatient().copy());
    qr.addItem()
        .setLinkId("exercise")
        .setText("Exercise attempted")
        .addAnswer()
        .setValue(new BooleanType(true));
    DocumentReference document = new DocumentReference().setSubject(claim.getPatient().copy());
    document
        .addContent()
        .setAttachment(
            new Attachment()
                .setTitle("Stress report")
                .setContentType("application/pdf")
                .setSize(123));
    DocumentReference anotherPatient =
        new DocumentReference().setSubject(new Reference("Patient/other"));
    anotherPatient.addContent().setAttachment(new Attachment().setTitle("Other patient's report"));
    fhir.givenStored(qr, "qr");
    fhir.givenStored(document, "doc");
    fhir.givenStored(anotherPatient, "other");
    claim.addSupportingInfo().setValue(new Reference("QuestionnaireResponse/qr"));
    claim.addSupportingInfo().setValue(new Reference("DocumentReference/doc"));
    claim.addSupportingInfo().setValue(new Reference("DocumentReference/other"));
    claim
        .addSupportingInfo()
        .setValue(new Reference("https://example.org/fhir/DocumentReference/remote"));
    claim.addSupportingInfo().setValue(new Reference("DocumentReference/missing"));
    claim.addSupportingInfo().setValue(new Attachment().setTitle("Inline report"));

    PriorAuthReviewResponse review = service.review("claim-1");

    assertThat(review.pended()).isTrue();
    assertThat(review.lines())
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.service()).isEqualTo("Cardiac stress test");
              assertThat(line.rule().criteria())
                  .singleElement()
                  .satisfies(criterion -> assertThat(criterion.matched()).isTrue());
            });
    assertThat(review.answers())
        .containsExactly(
            new PriorAuthReviewResponse.Answer("exercise", "Exercise attempted", List.of("true")));
    assertThat(review.attachments())
        .containsExactly(
            new PriorAuthReviewResponse.Attachment("Stress report", "application/pdf", 123),
            new PriorAuthReviewResponse.Attachment("Inline report", null, null));
  }

  @ParameterizedTest
  @CsvSource({"APPROVE,A1,false", "DENY,A3,false", "PEND,A4,true"})
  void writesTheDecisionAndProvenanceInOneVersionAwareTransaction(
      Decision decision, String actionCode, boolean pended) {
    PriorAuthDecisionRequest request =
        decision == Decision.DENY
            ? new PriorAuthDecisionRequest(decision, "criteria", "Missing clinical rationale")
            : new PriorAuthDecisionRequest(decision, null, null);
    assertThat(service.decide("claim-1", request, "reviewer").pended()).isEqualTo(pended);
    assertThat(fhir.transactions())
        .singleElement()
        .satisfies(
            transaction -> {
              assertThat(transaction.getEntry()).hasSize(2).allMatch(entry -> entry.hasFullUrl());
              assertThat(transaction.getEntryFirstRep().getRequest().getIfMatch())
                  .isEqualTo("W/\"4\"");
              ClaimResponse updated = (ClaimResponse) transaction.getEntryFirstRep().getResource();
              assertThat(reviewAction(updated.getItemFirstRep().getAdjudicationFirstRep()))
                  .isEqualTo(actionCode);
              assertThat(reviewAction(updated.getItem().get(1).getAdjudicationFirstRep()))
                  .isEqualTo("A1");
              assertThat(updated.getPreAuthRef()).isEqualTo("PHP-PA-1");
              Provenance provenance = (Provenance) transaction.getEntry().get(1).getResource();
              assertThat(provenance.getAgentFirstRep().getWho().getIdentifier().getValue())
                  .isEqualTo("reviewer");
              assertThat(provenance.getTargetFirstRep().getReference())
                  .isEqualTo("ClaimResponse/response-1");
              assertThat(provenance.getRecorded().toInstant()).isEqualTo(NOW);
              if (decision == Decision.DENY) {
                assertThat(
                        updated.getItemFirstRep().getAdjudicationFirstRep().getReason().getText())
                    .isEqualTo("Missing clinical rationale");
                assertThat(provenance.getReasonFirstRep().getCodingFirstRep().getCode())
                    .isEqualTo("criteria");
              }
            });
  }

  @Test
  void rejectsFinalDecisionsAndConcurrentChanges() {
    fhir.givenTransactionResult(
        () -> {
          throw new PreconditionFailedException("new version");
        });
    var request = new PriorAuthDecisionRequest(Decision.APPROVE, null, null);
    assertThatThrownBy(() -> service.decide("claim-1", request, "reviewer"))
        .isInstanceOf(PriorAuthConflictException.class);

    response.getItem().removeFirst();
    fhir.givenSearchResult(ClaimResponse.class, bundleOf(response));
    assertThatThrownBy(() -> service.decide("claim-1", request, "reviewer"))
        .isInstanceOf(PriorAuthConflictException.class);
    assertThat(fhir.transactions()).hasSize(1);
  }

  @Test
  void excludesOtherClaims() {
    claim.setUse(Claim.Use.CLAIM);
    assertThatThrownBy(() -> service.review("claim-1"))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.review("absent"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  private void addAction(int sequence, String code) {
    response
        .addItem()
        .setItemSequence(sequence)
        .addAdjudication()
        .addExtension()
        .setUrl(REVIEW_ACTION)
        .addExtension(
            REVIEW_ACTION_CODE,
            new CodeableConcept(new Coding(X12_REVIEW_ACTION_SYSTEM, code, null)));
  }

  private static String reviewAction(ClaimResponse.AdjudicationComponent adjudication) {
    var action =
        (CodeableConcept)
            adjudication
                .getExtensionByUrl(REVIEW_ACTION)
                .getExtensionByUrl(REVIEW_ACTION_CODE)
                .getValue();
    return action.getCodingFirstRep().getCode();
  }
}
