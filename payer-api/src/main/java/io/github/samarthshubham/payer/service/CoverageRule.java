package io.github.samarthshubham.payer.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record CoverageRule(
    String system,
    String code,
    String display,
    Boolean paNeeded,
    Boolean docNeeded,
    String questionnaire,
    List<ApprovalCriterion> autoApproveCriteria,
    Boolean emergencyExempt) {

  public CoverageRule {
    autoApproveCriteria =
        autoApproveCriteria == null
            ? List.of()
            : Collections.unmodifiableList(new ArrayList<>(autoApproveCriteria));
  }

  public static CoverageRule noPriorAuthNeeded(String system, String code) {
    return new CoverageRule(system, code, null, false, false, null, List.of(), false);
  }

  public boolean priorAuthRequired(OrderPriority priority) {
    return paNeeded && !(emergencyExempt && priority != null && priority.isEmergency());
  }
}
