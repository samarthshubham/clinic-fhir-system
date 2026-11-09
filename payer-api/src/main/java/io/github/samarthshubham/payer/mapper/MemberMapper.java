package io.github.samarthshubham.payer.mapper;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import io.github.samarthshubham.payer.dto.MemberCoverage;
import io.github.samarthshubham.payer.dto.MemberResponse;
import io.github.samarthshubham.payer.dto.MemberSummary;
import io.github.samarthshubham.payer.dto.PlanSummary;
import io.github.samarthshubham.payer.dto.PriorAuthSummary;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InsurancePlan;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

@Component
public class MemberMapper {

  public static final String MEMBER_ID_SYSTEM =
      "http://praxishealthplan.example/fhir/identifier/member-id";
  public static final String INSURANCE_PLAN_SYSTEM =
      "http://praxishealthplan.example/fhir/identifier/insurance-plan";
  private static final String COVERAGE_CLASS_SYSTEM =
      "http://terminology.hl7.org/CodeSystem/coverage-class";

  private static final Comparator<Coverage> NEWEST_FIRST =
      Comparator.comparing(
              (Coverage coverage) -> date(coverage.getPeriod().getStartElement()),
              Comparator.nullsFirst(Comparator.naturalOrder()))
          .reversed();

  public MemberSummary toSummary(Patient patient, List<Coverage> coverages) {
    Optional<Coverage> current = currentCoverage(coverages);
    HumanName name = patient.getNameFirstRep();
    return new MemberSummary(
        patient.getIdElement().getIdPart(),
        memberId(patient),
        name.hasGiven() ? name.getGivenAsSingleString() : null,
        name.getFamily(),
        date(patient.getBirthDateElement()),
        patient.hasGender() ? patient.getGender().toCode() : null,
        current.flatMap(MemberMapper::planClass).map(Coverage.ClassComponent::getName).orElse(null),
        current.map(MemberMapper::status).orElse(null));
  }

  public MemberResponse toResponse(
      Patient patient,
      List<Coverage> coverages,
      Map<String, InsurancePlan> plansByPlanId,
      List<PriorAuthSummary> priorAuths) {
    MemberSummary summary = toSummary(patient, coverages);
    List<MemberCoverage> memberCoverages =
        coverages.stream()
            .sorted(NEWEST_FIRST)
            .map(
                coverage ->
                    toCoverage(
                        coverage,
                        planId(coverage) == null ? null : plansByPlanId.get(planId(coverage))))
            .toList();
    return new MemberResponse(
        summary.id(),
        summary.memberId(),
        summary.firstName(),
        summary.lastName(),
        summary.dateOfBirth(),
        summary.gender(),
        memberCoverages,
        priorAuths);
  }

  public PriorAuthSummary toPriorAuth(Claim claim, ClaimResponse response) {
    return new PriorAuthSummary(
        claim.getIdElement().getIdPart(),
        claim.hasStatus() ? claim.getStatus().toCode() : null,
        date(claim.getCreatedElement()),
        service(claim.getItemFirstRep().getProductOrService()),
        response != null && response.hasOutcome() ? response.getOutcome().toCode() : null,
        response != null ? response.getPreAuthRef() : null);
  }

  public static String memberId(Patient patient) {
    return patient.getIdentifier().stream()
        .filter(identifier -> MEMBER_ID_SYSTEM.equals(identifier.getSystem()))
        .map(Identifier::getValue)
        .findFirst()
        .orElse(null);
  }

  public static String planId(Coverage coverage) {
    return planClass(coverage).map(Coverage.ClassComponent::getValue).orElse(null);
  }

  public static String planId(InsurancePlan plan) {
    return plan.getIdentifier().stream()
        .filter(identifier -> INSURANCE_PLAN_SYSTEM.equals(identifier.getSystem()))
        .map(Identifier::getValue)
        .findFirst()
        .orElse(null);
  }

  private MemberCoverage toCoverage(Coverage coverage, InsurancePlan plan) {
    Optional<Coverage.ClassComponent> planClass = planClass(coverage);
    return new MemberCoverage(
        coverage.getIdElement().getIdPart(),
        status(coverage),
        date(coverage.getPeriod().getStartElement()),
        date(coverage.getPeriod().getEndElement()),
        coverage.hasPayor() ? coverage.getPayorFirstRep().getDisplay() : null,
        coverage.getRelationship().getCodingFirstRep().getCode(),
        planClass.isEmpty() && plan == null ? null : toPlan(planClass, plan));
  }

  private static PlanSummary toPlan(
      Optional<Coverage.ClassComponent> planClass, InsurancePlan plan) {
    String className = planClass.map(Coverage.ClassComponent::getName).orElse(null);
    return new PlanSummary(
        plan != null ? plan.getIdElement().getIdPart() : null,
        planClass.map(Coverage.ClassComponent::getValue).orElse(null),
        plan != null && plan.hasName() ? plan.getName() : className,
        plan != null ? plan.getPlanFirstRep().getType().getText() : null);
  }

  private static Optional<Coverage> currentCoverage(List<Coverage> coverages) {
    return coverages.stream()
        .sorted(NEWEST_FIRST)
        .filter(coverage -> coverage.getStatus() == Coverage.CoverageStatus.ACTIVE)
        .findFirst()
        .or(() -> coverages.stream().sorted(NEWEST_FIRST).findFirst());
  }

  private static Optional<Coverage.ClassComponent> planClass(Coverage coverage) {
    return coverage.getClass_().stream()
        .filter(
            coverageClass ->
                coverageClass.getType().getCoding().stream()
                    .anyMatch(
                        coding ->
                            COVERAGE_CLASS_SYSTEM.equals(coding.getSystem())
                                && "plan".equals(coding.getCode())))
        .findFirst();
  }

  private static String status(Coverage coverage) {
    return coverage.hasStatus() ? coverage.getStatus().toCode() : null;
  }

  private static String service(CodeableConcept concept) {
    if (concept.hasText()) {
      return concept.getText();
    }
    Coding coding = concept.getCodingFirstRep();
    return coding.hasDisplay() ? coding.getDisplay() : coding.getCode();
  }

  private static LocalDate date(BaseDateTimeType value) {
    if (value == null
        || value.isEmpty()
        || value.getPrecision().compareTo(TemporalPrecisionEnum.DAY) < 0) {
      return null;
    }
    return LocalDate.parse(value.getValueAsString().substring(0, 10));
  }
}
