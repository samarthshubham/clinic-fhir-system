package io.github.samarthshubham.payer.service;

import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.springframework.stereotype.Service;

@Service
public class PriorAuthAdjudicator {

  static final ApprovalCriterion.DenialReason NO_MEMBER_FOUND =
      new ApprovalCriterion.DenialReason(
          "no-member-found", "No member with this member id at Praxis Health Plan.");
  static final ApprovalCriterion.DenialReason NO_ACTIVE_COVERAGE =
      new ApprovalCriterion.DenialReason(
          "no-active-coverage", "The member has no active coverage on the date of the request.");

  private final CoverageRuleService coverageRuleService;

  public PriorAuthAdjudicator(CoverageRuleService coverageRuleService) {
    this.coverageRuleService = coverageRuleService;
  }

  /**
   * Applies the coverage rule for the service to the DTR answers by linkId. An unmet criterion with
   * a denial reason denies, but only once it is answered; a missing answer pends for review.
   */
  public PriorAuthDecision adjudicate(
      CodeableConcept service,
      OrderPriority priority,
      MemberCoverageCheck coverage,
      Map<String, String> answers) {
    if (!coverage.verified()) {
      return PriorAuthDecision.denied(
          coverage.status() == MemberCoverageCheck.Status.NO_MEMBER_FOUND
              ? NO_MEMBER_FOUND
              : NO_ACTIVE_COVERAGE);
    }
    CoverageRule rule = rule(service);
    if (!rule.priorAuthRequired(priority)) {
      return PriorAuthDecision.of(PriorAuthDecision.Outcome.NOT_REQUIRED);
    }
    List<ApprovalCriterion> criteria = rule.autoApproveCriteria();
    if (criteria.isEmpty()) {
      return PriorAuthDecision.of(PriorAuthDecision.Outcome.PENDED);
    }
    for (ApprovalCriterion criterion : criteria) {
      String answer = answers.get(criterion.linkId());
      if (criterion.denialReason() != null && answer != null && !criterion.isMetBy(answer)) {
        return PriorAuthDecision.denied(criterion.denialReason());
      }
    }
    boolean allMet =
        criteria.stream().allMatch(criterion -> criterion.isMetBy(answers.get(criterion.linkId())));
    return PriorAuthDecision.of(
        allMet ? PriorAuthDecision.Outcome.APPROVED : PriorAuthDecision.Outcome.PENDED);
  }

  CoverageRule rule(CodeableConcept service) {
    Coding coding =
        service.getCoding().stream()
            .filter(c -> coverageRuleService.hasRule(c.getSystem(), c.getCode()))
            .findFirst()
            .orElse(service.getCodingFirstRep());
    return coverageRuleService.findRule(coding.getSystem(), coding.getCode());
  }
}
