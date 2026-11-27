package io.github.samarthshubham.payer.service;

import static io.github.samarthshubham.payer.support.FhirClientStub.bundleOf;
import static io.github.samarthshubham.payer.support.GoldenFhir.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import io.github.samarthshubham.payer.mapper.MemberMapper;
import io.github.samarthshubham.payer.support.FhirClientStub;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClaimInquiryServiceTest {

  private final FhirClientStub payerHapi = new FhirClientStub();
  private final AuditEventService audit = mock(AuditEventService.class);
  private final ClaimInquiryService service =
      new ClaimInquiryService(
          payerHapi.client(),
          FhirContext.forR4Cached(),
          audit,
          Clock.fixed(Instant.parse("2026-08-20T15:17:05Z"), ZoneOffset.UTC));

  private Bundle request;
  private Claim inquiry;
  private Claim storedClaim;
  private ClaimResponse pended;
  private ClaimResponse approved;
  private Patient member;

  @BeforeEach
  void arrangeSubmittedClaimAndDecisions() {
    request = fixture(Bundle.class, "pas/inquire-request.json");
    inquiry = (Claim) request.getEntryFirstRep().getResource();
    storedClaim =
        (Claim)
            fixture(Bundle.class, "pas/request-polysomnography.json")
                .getEntryFirstRep()
                .getResource();
    storedClaim.setId("1");
    storedClaim.setPatient(new Reference("Patient/payer-gerald"));
    storedClaim.setProvider(new Reference("http://praxis.clinic/fhir/Organization/praxis-clinic"));
    storedClaim
        .getProvider()
        .setIdentifier(
            new org.hl7.fhir.r4.model.Identifier()
                .setSystem("http://hl7.org/fhir/sid/us-npi")
                .setValue("1987654328"));
    member = new Patient();
    member.addIdentifier().setSystem(MemberMapper.MEMBER_ID_SYSTEM).setValue("PHP-10003");
    payerHapi.givenStored(member, "payer-gerald");

    pended =
        (ClaimResponse)
            fixture(Bundle.class, "pas/response-pended.json").getEntryFirstRep().getResource();
    pended.setRequest(new Reference("Claim/1"));
    pended.setPatient(storedClaim.getPatient().copy());
    approved = pended.copy();
    approved.setId("3");
    approved.setDisposition("Approved");
    approved.setCreatedElement(new DateTimeType("2026-08-20T15:16:05Z"));
    payerHapi.givenSearchResult(Claim.class, bundleOf(storedClaim));
    payerHapi.givenSearchResult(ClaimResponse.class, bundleOf(approved, pended));
  }

  @Test
  void choosesNewestCurrentDecisionAndAuditsMatchedResources() {
    ClaimResponse unrelated = approved.copy();
    unrelated.setId("55");
    unrelated.setCreatedElement(new DateTimeType("2026-08-21T10:00:00Z"));
    unrelated.setRequest(new Reference("http://other.example/fhir/Claim/1"));
    ClaimResponse wrongPatient = unrelated.copy();
    wrongPatient.setRequest(new Reference("Claim/1"));
    wrongPatient.setPatient(new Reference("Patient/payer-sandra"));
    payerHapi.givenSearchResult(
        ClaimResponse.class, bundleOf(unrelated, approved, pended, wrongPatient));

    Parameters result = service.inquire(request, "clinic-api");

    ClaimResponse decision = returnedDecision(result);
    assertThat(decision.getIdPart()).isEqualTo("3");
    assertThat(decision.getDisposition()).isEqualTo("Approved");
    assertThat(decision.getPatient().getReference())
        .isEqualTo("http://praxis.clinic/fhir/Patient/gerald-horton");
    assertThat(payerHapi.lastSearchCriteria(Claim.class))
        .contains("identifier=http://praxis.clinic/fhir/identifier/claim|PX-PA-0001");
    assertThat(payerHapi.lastSearchCriteria(ClaimResponse.class)).contains("request=Claim/1");
    verify(audit)
        .record(
            new AuditEventService.InteropAudit(
                "Claim/$inquire",
                "PX-INQ-0001",
                "clinic-api",
                200,
                List.of("Claim/1", "ClaimResponse/3")));
    assertThat(payerHapi.transactions()).isEmpty();
  }

  @Test
  void usesAnEarlierAuthorizationNumberToFindTheCurrentDecision() {
    inquiry.getRelated().clear();
    inquiry
        .addItem()
        .addExtension(
            "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-authorizationNumber",
            new StringType(pended.getPreAuthRef()));
    approved.setPreAuthRef("PHP-PA-CURRENT");
    payerHapi.givenSearchResult(ClaimResponse.class, bundleOf(pended, storedClaim, approved));

    assertThat(returnedDecision(service.inquire(request, "clinic-api")).getPreAuthRef())
        .isEqualTo("PHP-PA-CURRENT");
    assertThat(payerHapi.searchCount(Claim.class)).isZero();
    assertThat(payerHapi.searchCount(ClaimResponse.class)).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"claim", "patient", "provider", "provider-npi", "missing-patient", "trace"})
  void mismatchesDoNotReturnADecisionOrReadDecisionHistory(String mismatch) {
    switch (mismatch) {
      case "claim" -> inquiry.getRelatedFirstRep().getReference().setValue("unknown");
      case "patient" -> member.getIdentifierFirstRep().setValue("PHP-10005");
      case "provider" ->
          storedClaim
              .getProvider()
              .setReference("http://other.example/fhir/Organization/praxis-clinic");
      case "provider-npi" ->
          ((Organization) request.getEntry().get(1).getResource())
              .getIdentifierFirstRep()
              .setValue("1234567893");
      case "missing-patient" -> storedClaim.getPatient().setReference("Patient/unknown");
      case "trace" -> {
        inquiry.getIdentifierFirstRep().setValue("PX-PA-0001");
        inquiry.getRelated().clear();
      }
      default -> throw new AssertionError(mismatch);
    }

    assertThat(service.inquire(request, "clinic-api").getParameter()).isEmpty();
    assertThat(payerHapi.searchCount(ClaimResponse.class)).isZero();
  }

  @Test
  void requiresTheExternalPatientReferenceAndRetainedMemberIdentifierForAnUnmappedPatient() {
    storedClaim.setPatient(new Reference("http://praxis.clinic/fhir/Patient/gerald-horton"));
    storedClaim.getPatient().setIdentifier(member.getIdentifierFirstRep().copy());
    approved.setPatient(storedClaim.getPatient().copy());
    payerHapi.givenSearchResult(ClaimResponse.class, bundleOf(approved));

    assertThat(returnedDecision(service.inquire(request, "clinic-api")).getIdPart()).isEqualTo("3");
    storedClaim.getPatient().getIdentifier().setValue("PHP-10004");
    assertThat(service.inquire(request, "clinic-api").getParameter()).isEmpty();
  }

  @Test
  void noStoredDecisionsReturnsEmptyParameters() {
    payerHapi.givenSearchResult(ClaimResponse.class, bundleOf());

    assertThat(service.inquire(request, "clinic-api").getParameter()).isEmpty();
  }

  @Test
  void invalidProfileYieldsAnOperationOutcomeAndNoQuery() {
    OperationOutcome outcome = new OperationOutcome();
    outcome
        .addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setCode(OperationOutcome.IssueType.STRUCTURE)
        .setDiagnostics("Invalid inquiry");
    payerHapi.givenOperationResult(() -> outcome);

    assertThatThrownBy(() -> service.inquire(request, "clinic-api"))
        .isInstanceOf(UnprocessableEntityException.class);
    assertThat(payerHapi.searchCount(Claim.class)).isZero();
  }

  @Test
  void invalidBundleIsRejectedBeforeValidation() {
    request.getEntry().removeFirst();
    assertThatThrownBy(() -> service.inquire(request, "clinic-api"))
        .isInstanceOf(InvalidRequestException.class);
    assertThat(payerHapi.operationInputs()).isEmpty();
  }

  private static ClaimResponse returnedDecision(Parameters result) {
    Bundle bundle = (Bundle) result.getParameterFirstRep().getResource();
    return (ClaimResponse) bundle.getEntryFirstRep().getResource();
  }
}
