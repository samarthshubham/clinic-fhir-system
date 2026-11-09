package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.dto.CoverageRequest;
import io.github.samarthshubham.clinic.dto.CoverageResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

@Component
public class CoverageMapper {

  public static final String PAYER_SYSTEM = "http://praxis.clinic/fhir/identifier/payer";
  public static final String MEMBER_ID_SYSTEM =
      "http://praxishealthplan.example/fhir/identifier/member-id";
  private static final String IDENTIFIER_TYPE_SYSTEM =
      "http://terminology.hl7.org/CodeSystem/v2-0203";
  private static final String RELATIONSHIP_SYSTEM =
      "http://terminology.hl7.org/CodeSystem/subscriber-relationship";
  private static final String COVERAGE_CLASS_SYSTEM =
      "http://terminology.hl7.org/CodeSystem/coverage-class";
  private static final String ORGANIZATION_TYPE_SYSTEM =
      "http://terminology.hl7.org/CodeSystem/organization-type";
  private static final String DEFAULT_RELATIONSHIP = "self";

  public Organization toPayerOrganization(CoverageRequest request) {
    Organization payer = new Organization();
    payer.getMeta().addProfile(UsCoreProfiles.ORGANIZATION);
    payer.addIdentifier().setSystem(PAYER_SYSTEM).setValue(request.payerId());
    payer.setActive(true);
    payer.addType(
        new CodeableConcept()
            .addCoding(
                new Coding()
                    .setSystem(ORGANIZATION_TYPE_SYSTEM)
                    .setCode("pay")
                    .setDisplay("Payer")));
    payer.setName(request.payerName());
    return payer;
  }

  public Coverage toFhir(CoverageRequest request, String payerOrganizationId) {
    Coverage coverage = new Coverage();
    coverage.getMeta().addProfile(UsCoreProfiles.COVERAGE);
    coverage
        .addIdentifier()
        .setType(
            new CodeableConcept()
                .addCoding(
                    new Coding()
                        .setSystem(IDENTIFIER_TYPE_SYSTEM)
                        .setCode("MB")
                        .setDisplay("Member Number")))
        .setSystem(MEMBER_ID_SYSTEM)
        .setValue(request.memberId());
    coverage.setStatus(Coverage.CoverageStatus.fromCode(request.status()));
    coverage.setSubscriberId(request.memberId());
    coverage.setBeneficiary(new Reference("Patient/" + request.patientId()));
    coverage.setRelationship(
        new CodeableConcept()
            .addCoding(
                new Coding()
                    .setSystem(RELATIONSHIP_SYSTEM)
                    .setCode(
                        request.relationship() != null
                            ? request.relationship()
                            : DEFAULT_RELATIONSHIP)));
    coverage.getPeriod().setStartElement(new DateTimeType(request.periodStart().toString()));
    if (request.periodEnd() != null) {
      coverage.getPeriod().setEndElement(new DateTimeType(request.periodEnd().toString()));
    }
    coverage.addPayor(
        new Reference("Organization/" + payerOrganizationId)
            .setIdentifier(new Identifier().setSystem(PAYER_SYSTEM).setValue(request.payerId()))
            .setDisplay(request.payerName()));
    if (request.planName() != null) {
      coverage
          .addClass_()
          .setType(
              new CodeableConcept()
                  .addCoding(new Coding().setSystem(COVERAGE_CLASS_SYSTEM).setCode("plan")))
          .setValue(request.planName())
          .setName(request.planName());
    }
    return coverage;
  }

  public CoverageResponse toResponse(Coverage coverage) {
    Reference payor = coverage.hasPayor() ? coverage.getPayorFirstRep() : new Reference();
    return new CoverageResponse(
        FhirValues.idOf(coverage),
        FhirValues.referenceId(coverage.getBeneficiary()),
        FhirValues.referenceId(payor),
        payor.hasIdentifier() ? payor.getIdentifier().getValue() : null,
        payor.getDisplay(),
        memberId(coverage),
        planName(coverage),
        coverage.hasStatus() ? coverage.getStatus().toCode() : null,
        FhirValues.localDate(coverage.getPeriod().getStartElement()),
        FhirValues.localDate(coverage.getPeriod().getEndElement()),
        coverage.getRelationship().hasCoding()
            ? coverage.getRelationship().getCodingFirstRep().getCode()
            : null);
  }

  private static String memberId(Coverage coverage) {
    return coverage.getIdentifier().stream()
        .filter(identifier -> MEMBER_ID_SYSTEM.equals(identifier.getSystem()))
        .map(Identifier::getValue)
        .findFirst()
        .orElse(coverage.getSubscriberId());
  }

  private static String planName(Coverage coverage) {
    return coverage.getClass_().stream()
        .filter(
            coverageClass ->
                coverageClass.getType().getCoding().stream()
                    .anyMatch(coding -> "plan".equals(coding.getCode())))
        .map(Coverage.ClassComponent::getName)
        .findFirst()
        .orElse(null);
  }
}
