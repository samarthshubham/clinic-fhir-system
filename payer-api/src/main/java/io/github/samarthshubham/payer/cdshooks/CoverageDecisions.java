package io.github.samarthshubham.payer.cdshooks;

import io.github.samarthshubham.payer.service.CoverageRule;
import io.github.samarthshubham.payer.service.CoverageRuleService;
import io.github.samarthshubham.payer.service.MemberCoverageCheck;
import io.github.samarthshubham.payer.service.OrderPriority;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

@Component
public class CoverageDecisions {

  static final String COVERAGE_INFORMATION =
      "http://hl7.org/fhir/us/davinci-crd/StructureDefinition/ext-coverage-information";
  static final String CRD_TEMP_SYSTEM = "http://hl7.org/fhir/us/davinci-crd/CodeSystem/temp";
  static final String CARD_TYPE_SYSTEM = "http://terminology.hl7.org/CodeSystem/cdshooks-card-type";

  private final CoverageRuleService coverageRuleService;

  public CoverageDecisions(CoverageRuleService coverageRuleService) {
    this.coverageRuleService = coverageRuleService;
  }

  CoverageDecision decide(CrdHook hook, ServiceRequest order, MemberCoverageCheck check) {
    Coding coding =
        order.getCode().getCoding().stream()
            .filter(c -> coverageRuleService.hasRule(c.getSystem(), c.getCode()))
            .findFirst()
            .orElse(order.getCode().getCodingFirstRep());
    boolean known = coverageRuleService.hasRule(coding.getSystem(), coding.getCode());
    CoverageRule rule = coverageRuleService.findRule(coding.getSystem(), coding.getCode());
    String serviceName =
        known && rule.display() != null ? rule.display() : serviceName(order, coding);
    if (!check.verified()) {
      return new CoverageDecision(check.status(), serviceName, false, false, false, null);
    }
    OrderPriority priority =
        order.hasPriority()
            ? OrderPriority.fromCode(order.getPriority().toCode())
            : OrderPriority.ROUTINE;
    boolean priorAuthNeeded = rule.priorAuthRequired(priority);
    boolean documentationNeeded = hook.primary() && priorAuthNeeded && rule.docNeeded();
    return new CoverageDecision(
        check.status(),
        serviceName,
        priorAuthNeeded,
        rule.paNeeded() && !priorAuthNeeded,
        documentationNeeded,
        documentationNeeded ? rule.questionnaire() : null);
  }

  Extension coverageInformation(
      CoverageDecision decision, String coverageReference, LocalDate date, String assertionId) {
    Extension information = new Extension(COVERAGE_INFORMATION);
    information.addExtension("coverage", new Reference(coverageReference));
    if (decision.verified()) {
      information.addExtension("covered", new CodeType("covered"));
      information.addExtension(
          "pa-needed", new CodeType(decision.priorAuthNeeded() ? "auth-needed" : "no-auth"));
      if (decision.documentationNeeded()) {
        information.addExtension("doc-needed", new CodeType("clinical"));
        information.addExtension(
            "reason",
            new CodeableConcept()
                .setText("Clinical documentation is needed for the prior authorization request"));
        information.addExtension("questionnaire", new CanonicalType(decision.questionnaire()));
      }
    } else {
      information.addExtension("covered", new CodeType("not-covered"));
      information.addExtension("reason", notVerifiedReason(decision.memberStatus()));
    }
    information.addExtension("date", new DateType(date.toString()));
    information.addExtension("coverage-assertion-id", new StringType(assertionId));
    return information;
  }

  /** The same hook call always gets the same ids, so retries and golden files are stable. */
  static String stableId(String hookInstance, String suffix) {
    return UUID.nameUUIDFromBytes((hookInstance + "|" + suffix).getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  private static CodeableConcept notVerifiedReason(MemberCoverageCheck.Status status) {
    boolean noMember = status == MemberCoverageCheck.Status.NO_MEMBER_FOUND;
    return new CodeableConcept(
            new Coding(
                CRD_TEMP_SYSTEM,
                noMember ? "no-member-found" : "no-active-coverage",
                noMember ? "Member not found" : "Coverage not active"))
        .setText(
            noMember
                ? "No member with this member id"
                : "The member has no active coverage on this date");
  }

  private static String serviceName(ServiceRequest order, Coding coding) {
    if (order.getCode().hasText()) {
      return order.getCode().getText();
    }
    if (coding.hasDisplay()) {
      return coding.getDisplay();
    }
    return coding.hasCode() ? coding.getCode() : "this order";
  }
}
