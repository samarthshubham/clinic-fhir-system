package io.github.samarthshubham.clinic.service.pas;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;

final class PasInquiryRequest {

  static final String PAS = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/";
  static final String CLAIM_IDENTIFIER_SYSTEM = "http://praxis.clinic/fhir/identifier/claim";
  private static final String TRACE_SYSTEM = "http://praxis.clinic/fhir/identifier/pas-inquiry";

  private PasInquiryRequest() {}

  static Bundle fromSubmission(Bundle submission) {
    if (!submission.hasEntry()
        || !(submission.getEntryFirstRep().getResource() instanceof Claim submitted)
        || submitted.getUse() != Claim.Use.PREAUTHORIZATION
        || submitted.getInsurance().size() != 1) {
      throw new IllegalArgumentException("Expected a PAS preauthorization submission");
    }
    Identifier original =
        submitted.getIdentifier().stream()
            .filter(identifier -> CLAIM_IDENTIFIER_SYSTEM.equals(identifier.getSystem()))
            .filter(Identifier::hasValue)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Missing clinic Claim identifier"));

    Bundle request = new Bundle().setType(Bundle.BundleType.COLLECTION);
    request.getMeta().addProfile(PAS + "profile-pas-inquiry-request-bundle");
    Claim claim =
        new Claim()
            .setStatus(Claim.ClaimStatus.ACTIVE)
            .setUse(Claim.Use.PREAUTHORIZATION)
            .setType(submitted.getType().copy())
            .setPriority(submitted.getPriority().copy());
    claim.getMeta().addProfile(PAS + "profile-claim-inquiry");
    claim.addRelated().setReference(original.copy());
    request.addEntry().setResource(claim);
    claim.setProvider(include(submission, request, submitted.getProvider()));
    claim.setInsurer(include(submission, request, submitted.getInsurer()));
    claim.setPatient(include(submission, request, submitted.getPatient()));
    claim
        .addInsurance()
        .setSequence(1)
        .setFocal(true)
        .setCoverage(include(submission, request, submitted.getInsuranceFirstRep().getCoverage()));
    return forInquiry(request, Instant.now());
  }

  static Bundle forInquiry(Bundle template, Instant now) {
    Bundle request = template.copy();
    Claim claim = claim(request);
    String trace = UUID.randomUUID().toString();
    claim.setId(trace);
    claim.setIdentifier(List.of(new Identifier().setSystem(TRACE_SYSTEM).setValue(trace)));
    claim.setCreatedElement(new DateTimeType(now.toString()));
    request.setIdentifier(new Identifier().setSystem(TRACE_SYSTEM).setValue(trace));
    request.getEntryFirstRep().setFullUrl("urn:uuid:" + trace);
    request.getTimestampElement().setValueAsString(now.toString());
    return request;
  }

  static Claim claim(Bundle request) {
    return (Claim) request.getEntryFirstRep().getResource();
  }

  static boolean matches(Reference expected, Reference received) {
    if (!expected.hasReference() || !received.hasReference()) {
      return false;
    }
    var expectedId = expected.getReferenceElement();
    var receivedId = received.getReferenceElement();
    if (receivedId.isAbsolute()) {
      return expectedId.toVersionless().getValue().equals(receivedId.toVersionless().getValue());
    }
    return expectedId
        .toUnqualifiedVersionless()
        .getValue()
        .equals(receivedId.toUnqualifiedVersionless().getValue());
  }

  private static Reference include(Bundle submission, Bundle request, Reference reference) {
    Bundle.BundleEntryComponent source =
        submission.getEntry().stream()
            .filter(entry -> entry.hasFullUrl() && entry.hasResource())
            .filter(entry -> matches(new Reference(entry.getFullUrl()), reference))
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException("Required PAS inquiry resource missing"));
    request.addEntry().setFullUrl(source.getFullUrl()).setResource(source.getResource().copy());
    return new Reference(source.getFullUrl());
  }
}
